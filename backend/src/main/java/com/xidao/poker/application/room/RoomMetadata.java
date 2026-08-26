package com.xidao.poker.application.room;

import java.time.Instant;

/** 大厅元数据；盲注、买入等规则仍由 Engine 的 GameConfig 持有。 */
public record RoomMetadata(String roomId, String roomName, Instant createdAt) {
    public RoomMetadata {
        if (roomId == null || roomId.isBlank()) throw new IllegalArgumentException("room id is required");
        if (roomName == null || roomName.isBlank()) throw new IllegalArgumentException("room name is required");
        if (createdAt == null) throw new IllegalArgumentException("created time is required");
    }
}
