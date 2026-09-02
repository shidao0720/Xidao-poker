package com.xidao.poker.application.arena;

import com.xidao.poker.engine.arena.ArenaSession;
import com.xidao.poker.engine.arena.ArenaSnapshot;
import com.xidao.poker.engine.arena.VehicleInput;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/** Serializes every mutation for one vehicle room; networking is invoked only after the lock is released. */
public final class ArenaRoomRuntime {
    private static final int COMMAND_CACHE_LIMIT = 2_048;

    private final ArenaSession session;
    private final String roomId;
    private final ArenaEventSink sink;
    private final Clock clock;
    private final Instant createdAt;
    private final ReentrantLock lock = new ReentrantLock(true);
    private final Map<String, Boolean> processedCommands = new LinkedHashMap<>();
    private final Map<String, Long> connectionEpochs = new LinkedHashMap<>();
    private long sequence;
    private Instant emptySince;

    public ArenaRoomRuntime(ArenaSession session, ArenaEventSink sink, Clock clock) {
        this.session = session;
        this.roomId = session.snapshot().roomId();
        this.sink = sink;
        this.clock = clock;
        this.createdAt = clock.instant();
        this.emptySince = createdAt;
    }

    public ArenaUpdate join(String playerId, String name, String avatarKey, long connectionEpoch) {
        ArenaUpdate update;
        lock.lock();
        try {
            Long currentEpoch = connectionEpochs.get(playerId);
            if (currentEpoch != null && connectionEpoch < currentEpoch) return snapshotUpdate(true);
            connectionEpochs.put(playerId, connectionEpoch);
            if (session.snapshot().players().stream().anyMatch(player -> player.id().equals(playerId))) {
                session.reconnect(playerId, name, avatarKey);
            } else {
                session.addPlayer(playerId, name, avatarKey);
            }
            emptySince = null;
            update = nextUpdate(false);
        } finally {
            lock.unlock();
        }
        sink.broadcast(roomId, update);
        return update;
    }

    public ArenaUpdate ready(String requestId, String playerId, long connectionEpoch, boolean ready) {
        return command(requestId, playerId, connectionEpoch, ignored -> session.setReady(playerId, ready));
    }

    public ArenaUpdate start(String requestId, String playerId, long connectionEpoch) {
        return command(requestId, playerId, connectionEpoch,
                ignored -> session.startRound(playerId, clock.millis()));
    }

    public ArenaUpdate input(String requestId, String playerId, long connectionEpoch, VehicleInput input) {
        validateRequestId(requestId);
        ArenaUpdate update;
        lock.lock();
        try {
            if (alreadyProcessed(requestId)) return snapshotUpdate(true);
            requireCurrentConnection(playerId, connectionEpoch);
            session.acceptInput(playerId, input);
            remember(requestId);
            update = nextUpdate(false);
        } finally {
            lock.unlock();
        }
        return update;
    }

    public ArenaUpdate disconnect(String playerId, long connectionEpoch) {
        ArenaUpdate update;
        lock.lock();
        try {
            if (!isCurrentConnection(playerId, connectionEpoch)) return snapshotUpdate(true);
            session.disconnect(playerId);
            update = nextUpdate(false);
        } finally {
            lock.unlock();
        }
        sink.broadcast(roomId, update);
        return update;
    }

    public ArenaUpdate expireDisconnect(String playerId, long connectionEpoch) {
        ArenaUpdate update;
        lock.lock();
        try {
            if (!isCurrentConnection(playerId, connectionEpoch)) return snapshotUpdate(true);
            boolean connected = session.snapshot().players().stream()
                    .filter(player -> player.id().equals(playerId))
                    .findFirst().map(player -> player.connected()).orElse(false);
            if (connected) return snapshotUpdate(true);
            session.removePlayer(playerId, clock.millis());
            connectionEpochs.remove(playerId);
            if (session.memberCount() == 0) emptySince = clock.instant();
            update = nextUpdate(false);
        } finally {
            lock.unlock();
        }
        sink.broadcast(roomId, update);
        return update;
    }

    public ArenaUpdate leave(String requestId, String playerId, long connectionEpoch) {
        validateRequestId(requestId);
        ArenaUpdate update;
        lock.lock();
        try {
            if (alreadyProcessed(requestId)) return snapshotUpdate(true);
            requireCurrentConnection(playerId, connectionEpoch);
            session.removePlayer(playerId, clock.millis());
            connectionEpochs.remove(playerId);
            remember(requestId);
            if (session.memberCount() == 0) emptySince = clock.instant();
            update = nextUpdate(false);
        } finally {
            lock.unlock();
        }
        sink.broadcast(roomId, update);
        return update;
    }

    public void tick() {
        ArenaUpdate update = null;
        lock.lock();
        try {
            if (session.phase() == com.xidao.poker.engine.arena.ArenaPhase.WAITING) return;
            session.tick(clock.millis());
            update = nextUpdate(false);
        } finally {
            lock.unlock();
        }
        if (update != null) sink.broadcast(roomId, update);
    }

    public ArenaUpdate current() {
        lock.lock();
        try {
            return snapshotUpdate(false);
        } finally {
            lock.unlock();
        }
    }

    public ArenaRoomSummary summary() {
        lock.lock();
        try {
            ArenaSnapshot snapshot = session.snapshot();
            return new ArenaRoomSummary(snapshot.roomId(), snapshot.roomName(), snapshot.phase(),
                    session.memberCount(), session.connectedCount(), session.maxPlayers(), createdAt);
        } finally {
            lock.unlock();
        }
    }

    public boolean removable(Instant now, java.time.Duration ttl) {
        lock.lock();
        try {
            return session.memberCount() == 0 && emptySince != null && !now.isBefore(emptySince.plus(ttl));
        } finally {
            lock.unlock();
        }
    }

    private ArenaUpdate command(String requestId, String playerId, long connectionEpoch,
                                Consumer<Void> mutation) {
        validateRequestId(requestId);
        ArenaUpdate update;
        lock.lock();
        try {
            if (alreadyProcessed(requestId)) return snapshotUpdate(true);
            requireCurrentConnection(playerId, connectionEpoch);
            mutation.accept(null);
            remember(requestId);
            update = nextUpdate(false);
        } finally {
            lock.unlock();
        }
        sink.broadcast(roomId, update);
        return update;
    }

    private ArenaUpdate nextUpdate(boolean duplicate) {
        return new ArenaUpdate(++sequence, session.snapshot(), duplicate);
    }

    private ArenaUpdate snapshotUpdate(boolean duplicate) {
        return new ArenaUpdate(sequence, session.snapshot(), duplicate);
    }

    private boolean isCurrentConnection(String playerId, long epoch) {
        return connectionEpochs.getOrDefault(playerId, -1L) == epoch;
    }

    private void requireCurrentConnection(String playerId, long epoch) {
        if (!isCurrentConnection(playerId, epoch)) throw new IllegalStateException("stale arena connection");
    }

    private boolean alreadyProcessed(String requestId) {
        return processedCommands.containsKey(requestId);
    }

    private void remember(String requestId) {
        processedCommands.put(requestId, Boolean.TRUE);
        while (processedCommands.size() > COMMAND_CACHE_LIMIT) {
            processedCommands.remove(processedCommands.keySet().iterator().next());
        }
    }

    private static void validateRequestId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{8,64}")) {
            throw new IllegalArgumentException("request id is invalid");
        }
    }
}
