package com.xidao.poker.application.room;

import com.xidao.poker.engine.game.GameConfig;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** HTTP 大厅用例的纯应用服务；Controller 后续只调用这里。 */
public final class RoomService {
    private final RoomRegistry registry;
    private final Clock clock;

    public RoomService(RoomRegistry registry, Clock clock) {
        if (registry == null) throw new IllegalArgumentException("room registry is required");
        if (clock == null) throw new IllegalArgumentException("clock is required");
        this.registry = registry;
        this.clock = clock;
    }

    public RoomSummary createRoom(
            String roomId,
            String roomName,
            GameConfig config
    ) {
        RoomMetadata metadata = new RoomMetadata(roomId, roomName, Instant.now(clock));
        return registry.create(metadata, config).summary();
    }

    /** 仅供确定性测试和问题重放；Controller 不应暴露 seed 参数。 */
    RoomSummary createRoom(
            String roomId,
            String roomName,
            GameConfig config,
            long deterministicSeed
    ) {
        RoomMetadata metadata = new RoomMetadata(roomId, roomName, Instant.now(clock));
        return registry.create(metadata, config, deterministicSeed).summary();
    }

    public List<RoomSummary> listRooms() {
        return registry.summaries();
    }

    public void removeEmptyRoom(String roomId) {
        registry.removeEmpty(roomId);
    }
}
