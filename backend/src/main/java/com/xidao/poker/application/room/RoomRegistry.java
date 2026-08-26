package com.xidao.poker.application.room;

import com.xidao.poker.engine.game.GameConfig;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 线程安全的房间运行实例目录。 */
public final class RoomRegistry {
    private final ConcurrentMap<String, RoomRuntime> rooms = new ConcurrentHashMap<>();

    RoomRuntime create(RoomMetadata metadata, GameConfig config) {
        return register(new RoomRuntime(metadata, config));
    }

    RoomRuntime create(RoomMetadata metadata, GameConfig config, long baseSeed) {
        return register(new RoomRuntime(metadata, config, baseSeed));
    }

    private RoomRuntime register(RoomRuntime runtime) {
        RoomMetadata metadata = runtime.metadata();
        RoomRuntime existing = rooms.putIfAbsent(metadata.roomId(), runtime);
        if (existing != null) {
            throw new RoomApplicationException(
                    RoomApplicationErrorCode.ROOM_ALREADY_EXISTS,
                    "room already exists"
            );
        }
        return runtime;
    }

    RoomRuntime create(
            RoomMetadata metadata,
            GameConfig config,
            long baseSeed,
            int replayCapacity,
            int commandCacheCapacity,
            int outboxCapacity
    ) {
        RoomRuntime runtime = new RoomRuntime(
                metadata,
                config,
                baseSeed,
                replayCapacity,
                commandCacheCapacity,
                outboxCapacity
        );
        return register(runtime);
    }

    Optional<RoomRuntime> find(String roomId) {
        return Optional.ofNullable(rooms.get(roomId));
    }

    RoomRuntime require(String roomId) {
        RoomRuntime runtime = rooms.get(roomId);
        if (runtime == null) {
            throw new RoomApplicationException(RoomApplicationErrorCode.ROOM_NOT_FOUND, "room not found");
        }
        return runtime;
    }

    List<RoomSummary> summaries() {
        return rooms.values().stream()
                .map(RoomRuntime::summary)
                .sorted(Comparator.comparing(RoomSummary::roomId))
                .toList();
    }

    void removeEmpty(String roomId) {
        RoomRuntime runtime = require(roomId);
        if (!runtime.closeIfRemovable()) {
            throw new RoomApplicationException(RoomApplicationErrorCode.ROOM_NOT_EMPTY, "room is not empty");
        }
        rooms.remove(roomId, runtime);
    }

    int size() {
        return rooms.size();
    }
}
