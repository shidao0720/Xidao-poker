package com.xidao.poker.application.arena;

import com.xidao.poker.engine.arena.ArenaSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class ArenaRoomRegistry {
    private static final Logger log = LoggerFactory.getLogger(ArenaRoomRegistry.class);
    private static final Duration EMPTY_TTL = Duration.ofSeconds(45);
    private final Map<String, ArenaRoomRuntime> rooms = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final ArenaEventSink sink;
    private final Clock clock;

    public ArenaRoomRegistry(ScheduledExecutorService scheduler, ArenaEventSink sink, Clock clock) {
        this.scheduler = scheduler;
        this.sink = sink;
        this.clock = clock;
        scheduler.scheduleAtFixedRate(this::tickRooms, 33, 33, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::cleanupRooms, 5, 5, TimeUnit.SECONDS);
    }

    public ArenaRoomSummary create(String roomName, int maxPlayers) {
        String cleanName = roomName == null ? "" : roomName.strip();
        if (cleanName.isEmpty() || cleanName.length() > 40) throw new IllegalArgumentException("room name must be 1..40 characters");
        String roomId = "arena_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        ArenaRoomRuntime runtime = new ArenaRoomRuntime(new ArenaSession(roomId, cleanName, maxPlayers), sink, clock);
        rooms.put(roomId, runtime);
        return runtime.summary();
    }

    public List<ArenaRoomSummary> list() {
        return rooms.values().stream().map(ArenaRoomRuntime::summary)
                .sorted(java.util.Comparator.comparing(ArenaRoomSummary::createdAt)).toList();
    }

    public ArenaRoomRuntime require(String roomId) {
        ArenaRoomRuntime runtime = rooms.get(roomId);
        if (runtime == null) throw new IllegalArgumentException("arena room was not found");
        return runtime;
    }

    public ScheduledExecutorService scheduler() { return scheduler; }

    private void tickRooms() {
        rooms.values().forEach(runtime -> {
            try {
                runtime.tick();
            } catch (RuntimeException error) {
                log.warn("ARENA_TICK_FAILED roomId={} code={}", runtime.summary().roomId(),
                        error.getClass().getSimpleName());
            }
        });
    }

    private void cleanupRooms() {
        rooms.entrySet().removeIf(entry -> entry.getValue().removable(clock.instant(), EMPTY_TTL));
    }

}
