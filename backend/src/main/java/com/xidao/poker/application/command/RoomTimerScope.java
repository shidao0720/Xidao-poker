package com.xidao.poker.application.command;

/** 安排计时器时，在房间锁内捕获的权威牌局坐标。 */
public record RoomTimerScope(String roomId, long handId, long turnId) {
    public RoomTimerScope {
        if (roomId == null || roomId.isBlank()) throw new IllegalArgumentException("room id is required");
        if (handId < 0 || turnId < 0) throw new IllegalArgumentException("hand and turn ids cannot be negative");
    }
}
