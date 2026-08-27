package com.xidao.poker.application.history;

import com.xidao.poker.engine.history.CompletedHandSnapshot;

import java.time.Instant;

/** 应用层交给历史适配器的不可变归档单元。 */
public record CompletedHandArchive(
        String roomName,
        Instant roomCreatedAt,
        Instant startedAt,
        Instant endedAt,
        CompletedHandSnapshot hand
) {
    public CompletedHandArchive {
        if (roomName == null || roomName.isBlank()) throw new IllegalArgumentException("room name is required");
        if (roomCreatedAt == null) throw new IllegalArgumentException("room creation time is required");
        if (startedAt == null || endedAt == null) throw new IllegalArgumentException("hand timestamps are required");
        if (endedAt.isBefore(startedAt)) throw new IllegalArgumentException("hand cannot end before it starts");
        if (hand == null) throw new IllegalArgumentException("completed hand is required");
    }

    public String gameId() {
        return hand.sessionId();
    }

    public long handId() {
        return hand.handId();
    }
}
