package com.xidao.poker.application.room;

import com.xidao.poker.engine.event.GameEvent;

import java.util.List;

/** 增量事件仍在缓存时返回 events；过旧或客户端序号超前时要求完整 Snapshot。 */
public record EventReplay(
        boolean snapshotRequired,
        long oldestAvailableSequence,
        long latestSequence,
        List<GameEvent> events
) {
    public EventReplay {
        events = events == null ? List.of() : List.copyOf(events);
    }
}
