package com.xidao.poker.engine.event;

import java.util.Map;

/** 可广播、可记录的不可变事件。sequence 由 GameSession 统一分配。 */
public record GameEvent(
        long sequence,
        GameEventType type,
        long handId,
        String playerId,
        Map<String, Object> data
) {
    public GameEvent {
        if (type == null) throw new IllegalArgumentException("event type is required");
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    public static GameEvent of(GameEventType type, long handId, String playerId, Map<String, Object> data) {
        return new GameEvent(0, type, handId, playerId, data);
    }

    public GameEvent withSequence(long nextSequence) {
        return new GameEvent(nextSequence, type, handId, playerId, data);
    }
}
