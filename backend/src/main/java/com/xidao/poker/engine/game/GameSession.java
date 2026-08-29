package com.xidao.poker.engine.game;

import com.xidao.poker.engine.action.ActionErrorCode;
import com.xidao.poker.engine.action.IllegalActionException;
import com.xidao.poker.engine.action.PlayerAction;
import com.xidao.poker.engine.event.GameEvent;
import com.xidao.poker.engine.event.GameEventType;
import com.xidao.poker.engine.history.CompletedHandSnapshot;
import com.xidao.poker.engine.player.Player;
import com.xidao.poker.engine.snapshot.ActionOptions;
import com.xidao.poker.engine.snapshot.GameSnapshot;
import com.xidao.poker.engine.snapshot.PlayerSnapshot;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * 整场房间游戏的聚合根，管理玩家、房主和连续多手牌。
 *
 * <p>公开写方法使用 synchronized 作为第一版房间级锁。观察者只存在于 Session，
 * 绝不会被传入 Hand；Hand 的行动轮转因此只面对经过筛选的本手参与者。</p>
 */
public final class GameSession {
    private final String id;
    private final GameConfig config;
    private final Random shuffleRandom;
    private final Map<String, Player> playersById = new LinkedHashMap<>();
    private String ownerId;
    private Hand currentHand;
    private long handCounter;
    private long sequence;
    private Integer lastButtonSeat;
    private boolean gameStarted;

    /** 正式运行入口：使用不可预测随机源洗牌。 */
    public GameSession(String id, GameConfig config) {
        this(id, config, new SecureRandom());
    }

    /** 测试与故障复现入口：相同 seed 和命令序列产生相同牌局。 */
    public GameSession(String id, GameConfig config, long deterministicSeed) {
        this(id, config, new Random(deterministicSeed));
    }

    private GameSession(String id, GameConfig config, Random shuffleRandom) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("session id is required");
        if (config == null) throw new IllegalArgumentException("game config is required");
        this.id = id;
        this.config = config;
        this.shuffleRandom = Objects.requireNonNull(shuffleRandom, "shuffle random");
    }

    public synchronized List<GameEvent> addPlayer(String playerId, String name) {
        if (playersById.containsKey(playerId)) throw new IllegalArgumentException("player already joined");
        if (playersById.size() >= config.maxPlayers()) throw new IllegalStateException("room is full");
        int seat = firstFreeSeat();
        Player player = new Player(playerId, name, seat, config.buyIn());
        if (handInProgress()) player.becomeSpectator();
        playersById.put(playerId, player);
        boolean inHand = handInProgress() && currentHand.containsPlayer(player.id()) && player.isInHand();
        List<GameEvent> raw = new ArrayList<>();
        raw.add(GameEvent.of(GameEventType.PLAYER_JOINED, currentHandId(), playerId, Map.ofEntries(
                Map.entry("name", name),
                Map.entry("seat", seat),
                Map.entry("stack", player.stack()),
                Map.entry("streetBet", player.streetBet()),
                Map.entry("totalContribution", player.totalContribution()),
                Map.entry("status", player.status().name()),
                Map.entry("connectionStatus", player.connectionStatus().name()),
                Map.entry("seatStatus", player.seatStatus().name()),
                Map.entry("handStatus", player.handStatus().name()),
                Map.entry("inHand", inHand),
                Map.entry("canAct", inHand && player.canAct()),
                Map.entry("ready", player.ready()),
                Map.entry("phase", phase().name())
        )));
        if (ownerId == null) {
            ownerId = playerId;
        } else {
            Player currentOwner = playersById.get(ownerId);
            if (currentOwner != null && currentOwner.isDisconnected()) {
                String previousOwner = ownerId;
                ownerId = playerId;
                raw.add(GameEvent.of(GameEventType.OWNER_CHANGED, currentHandId(), playerId, Map.of(
                        "previousOwnerId", previousOwner,
                        "ownerId", playerId
                )));
            }
        }
        return publish(raw);
    }

    public synchronized List<GameEvent> setReady(String playerId, boolean ready) {
        Player player = requirePlayer(playerId);
        if (player.isDisconnected() || player.isBusted()) {
            throw new IllegalActionException(ActionErrorCode.INVALID_ACTION, "player cannot change ready state");
        }
        player.setReady(ready);
        return publish(List.of(GameEvent.of(GameEventType.READY_CHANGED, currentHandId(), playerId, Map.of(
                "ready", ready,
                "phase", phase().name()
        ))));
    }

    public synchronized List<GameEvent> startGame(String requestedBy) {
        if (!Objects.equals(requestedBy, ownerId)) {
            throw new IllegalActionException(ActionErrorCode.INVALID_ACTION, "only the room owner can start");
        }
        if (handInProgress()) {
            throw new IllegalActionException(ActionErrorCode.INVALID_PHASE, "a hand is already in progress");
        }
        List<Player> eligible = eligibleForNextHand();
        if (eligible.size() < 2) {
            throw new IllegalStateException("at least two connected players with chips are required");
        }
        if (eligible.stream().anyMatch(p -> !p.ready())) {
            throw new IllegalStateException("all eligible players must be ready");
        }
        for (Player player : eligible) player.activateForNextHand();

        int buttonSeat = nextButtonSeat(eligible);
        lastButtonSeat = buttonSeat;
        handCounter++;
        currentHand = new Hand(handCounter, config, eligible, buttonSeat, shuffleRandom);

        List<GameEvent> raw = new ArrayList<>();
        if (!gameStarted) {
            gameStarted = true;
            raw.add(GameEvent.of(GameEventType.GAME_STARTED, handCounter, null, Map.of(
                    "sessionId", id,
                    "playerCount", eligible.size()
            )));
        }
        raw.addAll(currentHand.start());
        return publish(raw);
    }

    synchronized List<GameEvent> handle(PlayerAction action) {
        requireHandInProgress();
        return publish(currentHand.handle(action));
    }

    /** 网络/application 层必须使用带 handId 与 turnId 的入口，拒绝延迟或重放行动。 */
    public synchronized List<GameEvent> handle(
            long expectedHandId,
            long expectedTurnId,
            PlayerAction action
    ) {
        requireHandInProgress();
        if (currentHand.id() != expectedHandId) {
            throw new IllegalActionException(ActionErrorCode.STALE_HAND, "action belongs to an expired hand");
        }
        return publish(currentHand.handle(action, expectedTurnId));
    }

    public synchronized List<GameEvent> disconnect(String playerId) {
        Player player = requirePlayer(playerId);
        if (player.isDisconnected()) return List.of();
        List<GameEvent> raw;
        if (handInProgress() && currentHand.containsPlayer(playerId)) {
            raw = new ArrayList<>(currentHand.disconnect(playerId));
        } else {
            player.disconnect();
            raw = new ArrayList<>(List.of(GameEvent.of(
                    GameEventType.PLAYER_DISCONNECTED, currentHandId(), playerId, Map.of(
                            "seat", player.seat(),
                            "connectionStatus", player.connectionStatus().name(),
                            "handStatus", player.handStatus().name()
                    ))));
        }
        transferOwnerAfterDisconnect(playerId, raw);
        return publish(raw);
    }

    public synchronized List<GameEvent> reconnect(String playerId) {
        Player player = requirePlayer(playerId);
        if (!player.isDisconnected()) return List.of();
        List<GameEvent> raw;
        if (currentHand != null && currentHand.containsPlayer(playerId) && player.isDisconnected()) {
            raw = currentHand.reconnect(playerId);
        } else {
            player.reconnect();
            // 已错过当前手的有筹码玩家只能等待下一手，不能显示为本手 ACTIVE。
            if (handInProgress() && player.stack() > 0) player.becomeSpectator();
            raw = List.of(GameEvent.of(
                    GameEventType.PLAYER_RECONNECTED, currentHandId(), playerId, Map.of(
                            "seat", player.seat(),
                            "status", player.status().name(),
                            "connectionStatus", player.connectionStatus().name(),
                            "seatStatus", player.seatStatus().name(),
                            "handStatus", player.handStatus().name()
                    )));
        }
        return publish(raw);
    }

    /** 只能在玩家不属于进行中的 Hand 时真正移除；牌局中离开应先 disconnect。 */
    public synchronized List<GameEvent> removePlayer(String playerId) {
        Player player = requirePlayer(playerId);
        if (handInProgress() && currentHand.containsPlayer(playerId)) {
            throw new IllegalActionException(
                    ActionErrorCode.INVALID_PHASE,
                    "an in-hand player cannot be removed before the hand ends"
            );
        }
        playersById.remove(playerId);
        List<GameEvent> raw = new ArrayList<>();
        raw.add(GameEvent.of(GameEventType.PLAYER_LEFT, currentHandId(), playerId, Map.of(
                "seat", player.seat()
        )));
        if (Objects.equals(ownerId, playerId)) {
            String nextOwner = connectedPlayers().stream()
                    .min(Comparator.comparingInt(Player::seat))
                    .map(Player::id)
                    .orElse(null);
            ownerId = nextOwner;
            if (nextOwner != null) {
                raw.add(GameEvent.of(GameEventType.OWNER_CHANGED, currentHandId(), nextOwner, Map.of(
                        "previousOwnerId", playerId,
                        "ownerId", nextOwner
                )));
            }
        }
        return publish(raw);
    }

    public synchronized GameSnapshot snapshot(String viewerId) {
        requirePlayer(viewerId);
        List<PlayerSnapshot> playerSnapshots = playersById.values().stream()
                .sorted(Comparator.comparingInt(Player::seat))
                .map(player -> new PlayerSnapshot(
                        player.id(),
                        player.name(),
                        player.seat(),
                        player.stack(),
                        player.streetBet(),
                        player.totalContribution(),
                        player.status(),
                        player.connectionStatus(),
                        player.seatStatus(),
                        player.handStatus(),
                        handInProgress() && currentHand.containsPlayer(player.id()) && player.isInHand(),
                        handInProgress() && currentHand.containsPlayer(player.id()) && player.canAct(),
                        player.ready(),
                        currentHand == null ? List.of() : currentHand.visibleHoleCards(viewerId, player.id())
                ))
                .toList();

        return new GameSnapshot(
                id,
                ownerId,
                currentHandId(),
                phase(),
                currentHand == null ? null : currentHand.buttonSeat(),
                currentHand == null ? null : currentHand.smallBlindSeat(),
                currentHand == null ? null : currentHand.bigBlindSeat(),
                currentHand == null ? null : currentHand.currentActorSeat(),
                currentHand == null ? 0 : currentHand.currentTurnId(),
                currentHand == null ? 0 : currentHand.currentBet(),
                currentHand == null ? config.bigBlind() : currentHand.minimumRaise(),
                currentHand == null ? 0 : currentHand.potAmount(),
                currentHand == null ? List.of() : currentHand.currentPots(),
                currentHand == null ? List.of() : currentHand.communityCards(),
                playerSnapshots,
                currentHand == null ? ActionOptions.none() : currentHand.actionOptions(viewerId),
                currentHand == null ? List.of() : currentHand.awards(),
                currentHand == null ? List.of() : currentHand.revealedHands(),
                sequence
        );
    }

    public synchronized GamePhase phase() {
        if (currentHand != null) return currentHand.phase();
        return allEligibleReady() ? GamePhase.READY : GamePhase.WAITING;
    }

    private List<GameEvent> publish(List<GameEvent> rawEvents) {
        List<GameEvent> published = new ArrayList<>(rawEvents.size());
        for (GameEvent raw : rawEvents) {
            GameEvent sequenced = raw.withSequence(++sequence);
            published.add(sequenced);
        }
        return List.copyOf(published);
    }

    private List<Player> eligibleForNextHand() {
        return playersById.values().stream()
                .filter(p -> !p.isDisconnected())
                .filter(p -> p.stack() > 0)
                .sorted(Comparator.comparingInt(Player::seat))
                .toList();
    }

    private List<Player> connectedPlayers() {
        return playersById.values().stream().filter(p -> !p.isDisconnected()).toList();
    }

    private void transferOwnerAfterDisconnect(String playerId, List<GameEvent> raw) {
        if (!Objects.equals(playerId, ownerId)) return;
        String nextOwner = connectedPlayers().stream()
                .filter(p -> !p.id().equals(playerId))
                .min(Comparator.comparingInt(Player::seat))
                .map(Player::id)
                .orElse(null);
        if (nextOwner == null) return;
        ownerId = nextOwner;
        raw.add(GameEvent.of(GameEventType.OWNER_CHANGED, currentHandId(), nextOwner, Map.of(
                "previousOwnerId", playerId,
                "ownerId", nextOwner
        )));
    }

    private boolean allEligibleReady() {
        List<Player> eligible = eligibleForNextHand();
        return eligible.size() >= 2 && eligible.stream().allMatch(Player::ready);
    }

    private int nextButtonSeat(List<Player> eligible) {
        if (lastButtonSeat == null) {
            Player owner = playersById.get(ownerId);
            if (owner != null && eligible.contains(owner)) return owner.seat();
            return eligible.getFirst().seat();
        }
        for (int offset = 1; offset <= config.maxPlayers(); offset++) {
            int candidate = Math.floorMod(lastButtonSeat + offset, config.maxPlayers());
            if (eligible.stream().anyMatch(p -> p.seat() == candidate)) return candidate;
        }
        throw new IllegalStateException("no eligible button seat");
    }

    private int firstFreeSeat() {
        for (int seat = 0; seat < config.maxPlayers(); seat++) {
            int candidate = seat;
            if (playersById.values().stream().noneMatch(p -> p.seat() == candidate)) return seat;
        }
        throw new IllegalStateException("room is full");
    }

    public synchronized boolean handInProgress() {
        return currentHand != null && currentHand.phase() != GamePhase.ROUND_END;
    }

    private void requireHandInProgress() {
        if (!handInProgress()) {
            throw new IllegalActionException(ActionErrorCode.INVALID_PHASE, "no hand is in progress");
        }
    }

    private Player requirePlayer(String playerId) {
        Player player = playersById.get(playerId);
        if (player == null) throw new IllegalActionException(ActionErrorCode.UNKNOWN_PLAYER, "unknown player");
        return player;
    }

    public synchronized long currentHandId() {
        return currentHand == null ? 0 : currentHand.id();
    }

    public synchronized long currentTurnId() {
        return currentHand == null ? 0 : currentHand.currentTurnId();
    }

    public synchronized String currentActorPlayerId() {
        return currentHand == null ? null : currentHand.currentActorPlayerId();
    }

    public synchronized boolean containsPlayer(String playerId) {
        return playersById.containsKey(playerId);
    }

    public synchronized boolean isPlayerDisconnected(String playerId) {
        return requirePlayer(playerId).isDisconnected();
    }

    public synchronized boolean isCurrentHandParticipant(String playerId) {
        return currentHand != null && currentHand.containsPlayer(playerId);
    }

    public synchronized int playerCount() {
        return playersById.size();
    }

    public synchronized long lastSequence() {
        return sequence;
    }

    /** 已结算手牌的敏感内部投影；网络层不得调用或序列化。 */
    public synchronized CompletedHandSnapshot completedHandSnapshot() {
        if (currentHand == null) throw new IllegalStateException("no hand has been created");
        return currentHand.completedSnapshot(id);
    }

    public String id() { return id; }
    public GameConfig config() { return config; }
    public synchronized String ownerId() { return ownerId; }
    // 仅供同包引擎测试与诊断使用，避免应用层绕过 Session 锁直接修改聚合内部对象。
    synchronized Hand currentHand() { return currentHand; }
    synchronized List<Player> players() { return List.copyOf(playersById.values()); }
}
