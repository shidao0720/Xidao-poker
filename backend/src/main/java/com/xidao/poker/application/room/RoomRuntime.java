package com.xidao.poker.application.room;

import com.xidao.poker.application.command.PlayerActionCommand;
import com.xidao.poker.application.command.StartGameCommand;
import com.xidao.poker.application.command.TurnTimeoutCommand;
import com.xidao.poker.application.history.CompletedHandArchive;
import com.xidao.poker.engine.action.ActionErrorCode;
import com.xidao.poker.engine.action.ActionType;
import com.xidao.poker.engine.action.IllegalActionException;
import com.xidao.poker.engine.action.PlayerAction;
import com.xidao.poker.engine.event.GameEvent;
import com.xidao.poker.engine.event.GameEventType;
import com.xidao.poker.engine.game.GameConfig;
import com.xidao.poker.engine.game.GameSession;
import com.xidao.poker.engine.snapshot.GameSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 一个运行中的房间实例。它是应用层原子执行边界，持有连接、有限事件缓存和有序出站队列；
 * 所有德州扑克规则仍只由 GameSession 裁决。
 */
public final class RoomRuntime {
    private static final Logger log = LoggerFactory.getLogger(RoomRuntime.class);

    public static final int DEFAULT_REPLAY_EVENTS = 512;
    public static final int DEFAULT_COMMAND_CACHE = 1_024;
    public static final int DEFAULT_OUTBOX_DELIVERIES = 1_024;

    private final RoomMetadata metadata;
    private final GameSession gameSession;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock(true);
    private final Map<String, MemberConnection> connections = new LinkedHashMap<>();
    private final Set<String> pendingRemoval = new LinkedHashSet<>();
    private final Deque<GameEvent> recentEvents = new ArrayDeque<>();
    private final LinkedHashMap<CommandKey, Boolean> processedCommands = new LinkedHashMap<>();
    private final Deque<RoomDelivery> outbox = new ArrayDeque<>();
    private final AtomicBoolean deliveryDrainRunning = new AtomicBoolean();
    private final int replayCapacity;
    private final int commandCacheCapacity;
    private final int outboxCapacity;
    private Instant currentHandStartedAt;
    private Instant emptySince;
    private boolean closed;

    RoomRuntime(RoomMetadata metadata, GameConfig config, long baseSeed) {
        this(
                metadata,
                config,
                baseSeed,
                DEFAULT_REPLAY_EVENTS,
                DEFAULT_COMMAND_CACHE,
                DEFAULT_OUTBOX_DELIVERIES,
                Clock.systemUTC()
        );
    }

    RoomRuntime(RoomMetadata metadata, GameConfig config) {
        this(metadata, config, null, DEFAULT_REPLAY_EVENTS, DEFAULT_COMMAND_CACHE,
                DEFAULT_OUTBOX_DELIVERIES, Clock.systemUTC());
    }

    RoomRuntime(RoomMetadata metadata, GameConfig config, Clock clock) {
        this(metadata, config, null, DEFAULT_REPLAY_EVENTS, DEFAULT_COMMAND_CACHE,
                DEFAULT_OUTBOX_DELIVERIES, clock);
    }

    RoomRuntime(
            RoomMetadata metadata,
            GameConfig config,
            long baseSeed,
            int replayCapacity,
            int commandCacheCapacity,
            int outboxCapacity
    ) {
        this(metadata, config, Long.valueOf(baseSeed), replayCapacity, commandCacheCapacity,
                outboxCapacity, Clock.systemUTC());
    }

    RoomRuntime(
            RoomMetadata metadata,
            GameConfig config,
            long baseSeed,
            int replayCapacity,
            int commandCacheCapacity,
            int outboxCapacity,
            Clock clock
    ) {
        this(metadata, config, Long.valueOf(baseSeed), replayCapacity, commandCacheCapacity,
                outboxCapacity, clock);
    }

    private RoomRuntime(
            RoomMetadata metadata,
            GameConfig config,
            Long deterministicSeed,
            int replayCapacity,
            int commandCacheCapacity,
            int outboxCapacity,
            Clock clock
    ) {
        if (metadata == null) throw new IllegalArgumentException("room metadata is required");
        if (config == null) throw new IllegalArgumentException("game config is required");
        if (replayCapacity <= 0) throw new IllegalArgumentException("replay capacity must be positive");
        if (commandCacheCapacity <= 0) throw new IllegalArgumentException("command cache must be positive");
        if (outboxCapacity < config.maxPlayers()) {
            throw new IllegalArgumentException("outbox must fit one snapshot per room member");
        }
        if (clock == null) throw new IllegalArgumentException("clock is required");
        this.metadata = metadata;
        this.gameSession = deterministicSeed == null
                ? new GameSession(metadata.roomId(), config)
                : new GameSession(metadata.roomId(), config, deterministicSeed);
        this.replayCapacity = replayCapacity;
        this.commandCacheCapacity = commandCacheCapacity;
        this.outboxCapacity = outboxCapacity;
        this.clock = clock;
        this.emptySince = Instant.now(clock);
    }

    RoomExecutionResult join(
            String commandId,
            String playerId,
            String playerName,
            String connectionId
    ) {
        lock.lock();
        try {
            requireOpen();
            CommandKey key = commandKey(commandId, playerId);
            if (isDuplicate(key)) {
                MemberConnection current = requireConnection(playerId);
                if (current.state() != ConnectionState.CONNECTED
                        || !current.connectionId().equals(connectionId)) {
                    throw staleConnection();
                }
                return duplicateResult(playerId, true);
            }
            if (gameSession.containsPlayer(playerId)) {
                throw new RoomApplicationException(
                        RoomApplicationErrorCode.MEMBER_ALREADY_CONNECTED,
                        "player already belongs to this room"
                );
            }
            MemberConnection connection = new MemberConnection(connectionId, 1, ConnectionState.CONNECTED);
            List<GameEvent> events = gameSession.addPlayer(playerId, playerName);
            connections.put(playerId, connection);
            return complete(key, events, Set.of(playerId), false, connection.epoch(), false);
        } finally {
            lock.unlock();
        }
    }

    RoomExecutionResult setReady(
            String commandId,
            String playerId,
            String connectionId,
            long connectionEpoch,
            boolean ready
    ) {
        lock.lock();
        try {
            CommandKey key = commandKey(commandId, playerId);
            requireConnected(playerId, connectionId, connectionEpoch);
            if (isDuplicate(key)) return duplicateResult(playerId, true);
            return complete(
                    key,
                    gameSession.setReady(playerId, ready),
                    Set.of(),
                    false,
                    connectionEpoch,
                    false
            );
        } finally {
            lock.unlock();
        }
    }

    RoomExecutionResult startGame(StartGameCommand command) {
        if (!metadata.roomId().equals(command.roomId())) {
            throw new IllegalArgumentException("command belongs to another room");
        }
        lock.lock();
        try {
            CommandKey key = commandKey(command.commandId(), command.playerId());
            requireConnected(command.playerId(), command.connectionId(), command.connectionEpoch());
            if (isDuplicate(key)) return duplicateResult(command.playerId(), true);
            if (gameSession.currentHandId() != command.expectedPreviousHandId()) {
                throw new IllegalActionException(
                        ActionErrorCode.STALE_HAND,
                        "start command belongs to an expired room state"
                );
            }
            return complete(
                    key,
                    gameSession.startGame(command.playerId()),
                    Set.of(),
                    true,
                    command.connectionEpoch(),
                    false
            );
        } finally {
            lock.unlock();
        }
    }

    RoomExecutionResult act(PlayerActionCommand command) {
        if (!metadata.roomId().equals(command.roomId())) {
            throw new IllegalArgumentException("command belongs to another room");
        }
        lock.lock();
        try {
            CommandKey key = commandKey(command.commandId(), command.playerId());
            requireConnected(command.playerId(), command.connectionId(), command.connectionEpoch());
            if (isDuplicate(key)) return duplicateResult(command.playerId(), true);
            List<GameEvent> events = gameSession.handle(
                    command.handId(),
                    command.turnId(),
                    command.toPlayerAction()
            );
            return complete(
                    key,
                    events,
                    Set.of(),
                    false,
                    command.connectionEpoch(),
                    false
            );
        } finally {
            lock.unlock();
        }
    }

    RoomExecutionResult disconnect(
            String commandId,
            String playerId,
            String connectionId,
            long connectionEpoch
    ) {
        lock.lock();
        try {
            CommandKey key = commandKey(commandId, playerId);
            MemberConnection current = requireConnection(playerId);
            if (isDuplicate(key)) {
                if (!current.matches(connectionId, connectionEpoch)) throw staleConnection();
                return duplicateResult(playerId, false);
            }
            if (!current.matches(connectionId, connectionEpoch) || current.state() != ConnectionState.CONNECTED) {
                return withoutRequesterSnapshot(
                        complete(key, List.of(), Set.of(), false, current.epoch(), true));
            }
            List<GameEvent> events = gameSession.isPlayerDisconnected(playerId)
                    ? List.of()
                    : gameSession.disconnect(playerId);
            connections.put(playerId, current.reconnecting());
            return complete(key, events, Set.of(), false, current.epoch(), false);
        } finally {
            lock.unlock();
        }
    }

    RoomExecutionResult reconnect(
            String commandId,
            String playerId,
            long expectedConnectionEpoch,
            String newConnectionId
    ) {
        if (expectedConnectionEpoch <= 0) {
            throw new IllegalArgumentException("expected connection epoch must be positive");
        }
        lock.lock();
        try {
            CommandKey key = commandKey(commandId, playerId);
            MemberConnection previous = requireConnection(playerId);
            if (isDuplicate(key)) {
                if (!previous.connectionId().equals(newConnectionId)
                        || previous.epoch() != Math.incrementExact(expectedConnectionEpoch)) {
                    throw staleConnection();
                }
                return duplicateResult(playerId, previous.state() == ConnectionState.CONNECTED);
            }
            if (previous.epoch() != expectedConnectionEpoch) throw staleConnection();
            if (previous.state() == ConnectionState.CLOSED) {
                throw new RoomApplicationException(
                        RoomApplicationErrorCode.MEMBER_NOT_CONNECTED,
                        "player has already left the room"
                );
            }
            MemberConnection reconnected = previous.reconnect(newConnectionId);
            List<GameEvent> events = gameSession.isPlayerDisconnected(playerId)
                    ? gameSession.reconnect(playerId)
                    : List.of();
            connections.put(playerId, reconnected);
            return complete(key, events, Set.of(playerId), false, reconnected.epoch(), false);
        } finally {
            lock.unlock();
        }
    }

    RoomExecutionResult leave(
            String commandId,
            String playerId,
            String connectionId,
            long connectionEpoch
    ) {
        lock.lock();
        try {
            CommandKey key = commandKey(commandId, playerId);
            if (isDuplicate(key)) {
                MemberConnection current = connections.get(playerId);
                if (current != null && !current.matches(connectionId, connectionEpoch)) throw staleConnection();
                return duplicateResult(playerId, false);
            }
            MemberConnection connection = requireConnected(playerId, connectionId, connectionEpoch);
            return leaveInternal(key, playerId, connection, false);
        } finally {
            lock.unlock();
        }
    }

    /** 断线宽限到期后由应用层 Scheduler 调用；旧 epoch 的任务会被忽略。 */
    RoomExecutionResult expireDisconnected(
            String commandId,
            String playerId,
            long connectionEpoch
    ) {
        lock.lock();
        try {
            CommandKey key = commandKey(commandId, playerId);
            if (isDuplicate(key)) return duplicateResult(playerId, false);
            MemberConnection connection = requireConnection(playerId);
            if (connection.epoch() != connectionEpoch || connection.state() != ConnectionState.RECONNECTING) {
                return complete(key, List.of(), Set.of(), false, connection.epoch(), true);
            }
            return leaveInternal(key, playerId, connection, false);
        } finally {
            lock.unlock();
        }
    }

    /** 无响应玩家：可 Check 时自动 Check，面对下注时自动 Fold。 */
    RoomExecutionResult timeout(TurnTimeoutCommand command) {
        if (!metadata.roomId().equals(command.roomId())) {
            throw new IllegalArgumentException("timeout belongs to another room");
        }
        lock.lock();
        try {
            CommandKey key = commandKey(command.commandId(), command.playerId());
            if (isDuplicate(key)) return duplicateResult(command.playerId(), false);
            if (!gameSession.containsPlayer(command.playerId())
                    || gameSession.currentHandId() != command.handId()
                    || gameSession.currentTurnId() != command.turnId()
                    || !command.playerId().equals(gameSession.currentActorPlayerId())) {
                return complete(key, List.of(), Set.of(), false, connectionEpoch(command.playerId()), true);
            }

            GameSnapshot snapshot = gameSession.snapshot(command.playerId());
            PlayerAction automaticAction;
            if (snapshot.legalActions().contains(ActionType.CHECK)) {
                automaticAction = PlayerAction.check(command.playerId());
            } else if (snapshot.legalActions().contains(ActionType.FOLD)) {
                automaticAction = PlayerAction.fold(command.playerId());
            } else {
                return complete(key, List.of(), Set.of(), false, connectionEpoch(command.playerId()), true);
            }
            List<GameEvent> events = gameSession.handle(
                    command.handId(),
                    command.turnId(),
                    automaticAction
            );
            return complete(
                    key,
                    events,
                    Set.of(),
                    false,
                    connectionEpoch(command.playerId()),
                    false
            );
        } finally {
            lock.unlock();
        }
    }

    /** 将查看者专属快照按房间顺序加入 outbox，供加入、刷新和主动重同步使用。 */
    GameSnapshot requestSnapshot(String playerId, String connectionId, long connectionEpoch) {
        lock.lock();
        try {
            requireConnected(playerId, connectionId, connectionEpoch);
            GameSnapshot snapshot = gameSession.snapshot(playerId);
            enqueueDeliveries(List.of(), Map.of(playerId, snapshot));
            return snapshot;
        } finally {
            lock.unlock();
        }
    }

    EventReplay replayAfter(
            String playerId,
            String connectionId,
            long connectionEpoch,
            long afterSequence
    ) {
        if (afterSequence < 0) throw new IllegalArgumentException("sequence cannot be negative");
        lock.lock();
        try {
            requireConnected(playerId, connectionId, connectionEpoch);
            long latest = gameSession.lastSequence();
            if (afterSequence > latest) {
                return new EventReplay(true, oldestAvailable(latest), latest, List.of());
            }
            if (afterSequence == latest) {
                return new EventReplay(false, oldestAvailable(latest), latest, List.of());
            }
            if (recentEvents.isEmpty()) {
                return new EventReplay(true, latest + 1, latest, List.of());
            }
            long oldest = recentEvents.getFirst().sequence();
            if (afterSequence < oldest - 1) {
                return new EventReplay(true, oldest, latest, List.of());
            }
            List<GameEvent> events = recentEvents.stream()
                    .filter(event -> event.sequence() > afterSequence)
                    .toList();
            return new EventReplay(false, oldest, latest, events);
        } finally {
            lock.unlock();
        }
    }

    /** 单一发送器按返回顺序消费；实际网络 I/O 不在房间锁内执行。 */
    List<RoomDelivery> drainOutbox() {
        lock.lock();
        try {
            List<RoomDelivery> deliveries = List.copyOf(outbox);
            outbox.clear();
            return deliveries;
        } finally {
            lock.unlock();
        }
    }

    /** 增量广播失败时，以当前权威状态为每个在线查看者生成隐私隔离的恢复快照。 */
    void enqueueRecoverySnapshots() {
        lock.lock();
        try {
            Map<String, GameSnapshot> snapshots = new LinkedHashMap<>();
            for (String playerId : connectedPlayerIds()) {
                if (gameSession.containsPlayer(playerId)) {
                    snapshots.put(playerId, gameSession.snapshot(playerId));
                }
            }
            enqueueDeliveries(List.of(), snapshots);
        } finally {
            lock.unlock();
        }
    }

    boolean tryStartDeliveryDrain() {
        return deliveryDrainRunning.compareAndSet(false, true);
    }

    void stopDeliveryDrain() {
        deliveryDrainRunning.set(false);
    }

    boolean isDeliveryDrainRunning() {
        return deliveryDrainRunning.get();
    }

    RoomSummary summary() {
        lock.lock();
        try {
            int connected = (int) connections.values().stream()
                    .filter(connection -> connection.state() == ConnectionState.CONNECTED)
                    .count();
            return new RoomSummary(
                    metadata.roomId(),
                    metadata.roomName(),
                    metadata.createdAt(),
                    gameSession.phase(),
                    gameSession.playerCount(),
                    connected,
                    gameSession.config().smallBlind(),
                    gameSession.config().bigBlind(),
                    gameSession.config().buyIn(),
                    gameSession.config().maxPlayers()
            );
        } finally {
            lock.unlock();
        }
    }

    Optional<MemberConnection> connection(String playerId) {
        lock.lock();
        try {
            return Optional.ofNullable(connections.get(playerId));
        } finally {
            lock.unlock();
        }
    }

    int replaySize() {
        lock.lock();
        try {
            return recentEvents.size();
        } finally {
            lock.unlock();
        }
    }

    int processedCommandCount() {
        lock.lock();
        try {
            return processedCommands.size();
        } finally {
            lock.unlock();
        }
    }

    int pendingDeliveryCount() {
        lock.lock();
        try {
            return outbox.size();
        } finally {
            lock.unlock();
        }
    }

    boolean isRemovable() {
        lock.lock();
        try {
            return removableNow();
        } finally {
            lock.unlock();
        }
    }

    /** 与 join 使用同一把锁，消除“检查为空后又有人加入”的删除竞态。 */
    boolean closeIfRemovable() {
        lock.lock();
        try {
            if (!removableNow()) return false;
            closed = true;
            return true;
        } finally {
            lock.unlock();
        }
    }

    /** 与 join 共用房间锁；只有空置时间达标且发送队列已清空时才原子关闭。 */
    boolean closeIfEmptySince(Instant cutoff) {
        if (cutoff == null) throw new IllegalArgumentException("empty-room cutoff is required");
        lock.lock();
        try {
            if (emptySince == null || emptySince.isAfter(cutoff) || !removableNow()) return false;
            closed = true;
            return true;
        } finally {
            lock.unlock();
        }
    }

    RoomMetadata metadata() {
        return metadata;
    }

    private RoomExecutionResult leaveInternal(
            CommandKey key,
            String playerId,
            MemberConnection connection,
            boolean ignored
    ) {
        List<GameEvent> events;
        if (gameSession.handInProgress() && gameSession.isCurrentHandParticipant(playerId)) {
            events = gameSession.isPlayerDisconnected(playerId)
                    ? List.of()
                    : gameSession.disconnect(playerId);
            pendingRemoval.add(playerId);
            connections.put(playerId, connection.close());
        } else {
            events = gameSession.removePlayer(playerId);
            connections.remove(playerId);
        }
        return complete(key, events, Set.of(), false, connection.epoch(), ignored);
    }

    private RoomExecutionResult complete(
            CommandKey key,
            List<GameEvent> initialEvents,
            Set<String> explicitSnapshotTargets,
            boolean snapshotAll,
            long connectionEpoch,
            boolean ignored
    ) {
        List<GameEvent> events = new ArrayList<>(initialEvents);
        Instant commandTime = Instant.now(clock);
        if (events.stream().anyMatch(event -> event.type() == GameEventType.HAND_STARTED)) {
            currentHandStartedAt = commandTime;
        }
        List<CompletedHandArchive> completedHands = List.of();
        if (events.stream().anyMatch(event -> event.type() == GameEventType.HAND_ENDED)) {
            Instant startedAt = currentHandStartedAt == null ? commandTime : currentHandStartedAt;
            try {
                completedHands = List.of(new CompletedHandArchive(
                        metadata.roomName(),
                        metadata.createdAt(),
                        startedAt,
                        commandTime,
                        gameSession.completedHandSnapshot()
                ));
            } catch (RuntimeException error) {
                log.error("HAND_HISTORY_CAPTURE_FAILED roomId={} handId={} code={}",
                        metadata.roomId(), gameSession.currentHandId(),
                        error.getClass().getSimpleName(), error);
            }
            currentHandStartedAt = null;
            removePlayersWaitingForHandEnd(events);
        }
        refreshEmptySince(commandTime);
        appendRecentEvents(events);

        LinkedHashSet<String> targets = new LinkedHashSet<>(explicitSnapshotTargets);
        boolean revealOrNewHand = snapshotAll || events.stream().anyMatch(event ->
                event.type() == GameEventType.HOLE_CARDS_DEALT
                        || event.type() == GameEventType.SHOWDOWN
                        || event.type() == GameEventType.HAND_ENDED
        );
        if (revealOrNewHand) targets.addAll(connectedPlayerIds());
        if (events.stream().anyMatch(event -> event.type() == GameEventType.TURN_CHANGED)) {
            String actorId = gameSession.currentActorPlayerId();
            if (actorId != null) targets.add(actorId);
        }

        Map<String, GameSnapshot> snapshots = new LinkedHashMap<>();
        for (String target : targets) {
            MemberConnection connection = connections.get(target);
            if (connection != null
                    && connection.state() == ConnectionState.CONNECTED
                    && gameSession.containsPlayer(target)) {
                snapshots.put(target, gameSession.snapshot(target));
            }
        }
        enqueueDeliveries(events, snapshots);
        markProcessed(key);
        GameSnapshot requesterSnapshot = snapshotForConnectedViewer(key.playerId());
        return new RoomExecutionResult(
                false,
                ignored,
                connectionEpoch,
                gameSession.lastSequence(),
                events,
                requesterSnapshot,
                completedHands
        );
    }

    private void removePlayersWaitingForHandEnd(List<GameEvent> events) {
        for (String playerId : List.copyOf(pendingRemoval)) {
            if (gameSession.containsPlayer(playerId)) events.addAll(gameSession.removePlayer(playerId));
            pendingRemoval.remove(playerId);
            connections.remove(playerId);
        }
    }

    private void appendRecentEvents(List<GameEvent> events) {
        for (GameEvent event : events) {
            recentEvents.addLast(event);
            while (recentEvents.size() > replayCapacity) recentEvents.removeFirst();
        }
    }

    private void enqueueDeliveries(List<GameEvent> events, Map<String, GameSnapshot> snapshots) {
        List<RoomDelivery> additions = new ArrayList<>();
        if (!events.isEmpty()) additions.add(new RoomDelivery.Events(events));
        snapshots.forEach((playerId, snapshot) -> {
            MemberConnection connection = connections.get(playerId);
            if (connection != null && connection.state() == ConnectionState.CONNECTED) {
                additions.add(new RoomDelivery.Snapshot(
                        playerId,
                        connection.connectionId(),
                        connection.epoch(),
                        snapshot
                ));
            }
        });
        if (additions.isEmpty()) return;

        if (outbox.size() + additions.size() > outboxCapacity) {
            // 发送端落后时丢弃旧增量，以当前完整快照恢复所有在线成员，内存始终有上限。
            outbox.clear();
            for (String playerId : connectedPlayerIds()) {
                if (gameSession.containsPlayer(playerId)) {
                    MemberConnection connection = connections.get(playerId);
                    outbox.addLast(new RoomDelivery.Snapshot(
                            playerId,
                            connection.connectionId(),
                            connection.epoch(),
                            gameSession.snapshot(playerId)
                    ));
                }
            }
            return;
        }
        outbox.addAll(additions);
    }

    private List<String> connectedPlayerIds() {
        return connections.entrySet().stream()
                .filter(entry -> entry.getValue().state() == ConnectionState.CONNECTED)
                .map(Map.Entry::getKey)
                .toList();
    }

    private RoomExecutionResult duplicateResult(String playerId, boolean includeSnapshot) {
        GameSnapshot snapshot = includeSnapshot ? snapshotForConnectedViewer(playerId) : null;
        if (snapshot != null) enqueueDeliveries(List.of(), Map.of(playerId, snapshot));
        MemberConnection connection = connections.get(playerId);
        return new RoomExecutionResult(
                true,
                true,
                connection == null ? 0 : connection.epoch(),
                gameSession.lastSequence(),
                List.of(),
                snapshot,
                List.of()
        );
    }

    private RoomExecutionResult withoutRequesterSnapshot(RoomExecutionResult result) {
        return new RoomExecutionResult(
                result.duplicate(),
                result.ignored(),
                result.connectionEpoch(),
                result.lastSequence(),
                result.events(),
                null,
                result.completedHands()
        );
    }

    private GameSnapshot snapshotForConnectedViewer(String playerId) {
        MemberConnection connection = connections.get(playerId);
        return connection != null
                && connection.state() == ConnectionState.CONNECTED
                && gameSession.containsPlayer(playerId)
                ? gameSession.snapshot(playerId)
                : null;
    }

    private MemberConnection requireConnected(String playerId, String connectionId, long epoch) {
        MemberConnection connection = requireConnection(playerId);
        if (connection.state() != ConnectionState.CONNECTED) {
            throw new RoomApplicationException(
                    RoomApplicationErrorCode.MEMBER_NOT_CONNECTED,
                    "player has no active room connection"
            );
        }
        if (!connection.matches(connectionId, epoch)) {
            throw staleConnection();
        }
        return connection;
    }

    private MemberConnection requireConnection(String playerId) {
        MemberConnection connection = connections.get(playerId);
        if (connection == null) {
            throw new RoomApplicationException(
                    RoomApplicationErrorCode.MEMBER_NOT_CONNECTED,
                    "player does not belong to this runtime"
            );
        }
        return connection;
    }

    private long connectionEpoch(String playerId) {
        MemberConnection connection = connections.get(playerId);
        return connection == null ? 0 : connection.epoch();
    }

    private long oldestAvailable(long latest) {
        return recentEvents.isEmpty() ? latest + 1 : recentEvents.getFirst().sequence();
    }

    private void refreshEmptySince(Instant now) {
        if (gameSession.playerCount() == 0) {
            if (emptySince == null) emptySince = now;
        } else {
            emptySince = null;
        }
    }

    private boolean removableNow() {
        return !closed
                && gameSession.playerCount() == 0
                && pendingRemoval.isEmpty()
                && outbox.isEmpty()
                && !deliveryDrainRunning.get();
    }

    private void requireOpen() {
        if (closed) {
            throw new RoomApplicationException(
                    RoomApplicationErrorCode.ROOM_CLOSED,
                    "room runtime has already been removed"
            );
        }
    }

    private RoomApplicationException staleConnection() {
        return new RoomApplicationException(
                RoomApplicationErrorCode.STALE_CONNECTION,
                "command came from a replaced connection"
        );
    }

    private CommandKey commandKey(String commandId, String playerId) {
        if (commandId == null || commandId.isBlank()) throw new IllegalArgumentException("command id is required");
        if (playerId == null || playerId.isBlank()) throw new IllegalArgumentException("player id is required");
        return new CommandKey(playerId, commandId);
    }

    private boolean isDuplicate(CommandKey key) {
        return processedCommands.containsKey(key);
    }

    private void markProcessed(CommandKey key) {
        processedCommands.put(key, Boolean.TRUE);
        while (processedCommands.size() > commandCacheCapacity) {
            Iterator<CommandKey> iterator = processedCommands.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
    }

    private record CommandKey(String playerId, String commandId) {
    }
}
