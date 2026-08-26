package com.xidao.poker.application.room;

import com.xidao.poker.engine.event.GameEvent;
import com.xidao.poker.engine.snapshot.GameSnapshot;

import java.util.List;

/** 有序出站消息；WebSocket 适配器只负责消费，不参与游戏裁决。 */
public sealed interface RoomDelivery permits RoomDelivery.Events, RoomDelivery.Snapshot {
    record Events(List<GameEvent> events) implements RoomDelivery {
        public Events {
            events = events == null ? List.of() : List.copyOf(events);
            if (events.isEmpty()) throw new IllegalArgumentException("event delivery cannot be empty");
        }
    }

    record Snapshot(
            String playerId,
            String connectionId,
            long connectionEpoch,
            GameSnapshot snapshot
    ) implements RoomDelivery {
        public Snapshot {
            if (playerId == null || playerId.isBlank()) {
                throw new IllegalArgumentException("snapshot target is required");
            }
            if (connectionId == null || connectionId.isBlank()) {
                throw new IllegalArgumentException("snapshot connection is required");
            }
            if (connectionEpoch <= 0) throw new IllegalArgumentException("snapshot epoch must be positive");
            if (snapshot == null) throw new IllegalArgumentException("snapshot is required");
        }
    }
}
