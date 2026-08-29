package com.xidao.poker.application.room;

/** 当前应当拥有行动超时计时器的服务端权威目标。 */
public record RoomTurnTimerTarget(
        String roomId,
        String playerId,
        long handId,
        long turnId
) {
    public RoomTurnTimerTarget {
        if (roomId == null || roomId.isBlank()) throw new IllegalArgumentException("room id is required");
        if (playerId == null || playerId.isBlank()) throw new IllegalArgumentException("player id is required");
        if (handId <= 0 || turnId <= 0) throw new IllegalArgumentException("hand and turn ids must be positive");
    }
}
