package com.xidao.poker.application.room;

import com.xidao.poker.engine.event.GameEvent;
import com.xidao.poker.engine.snapshot.GameSnapshot;

import java.util.List;

/**
 * 命令确认只携带发起者自己的查看者快照。面向其他成员的快照只能通过
 * {@link RoomDelivery.Snapshot} 定向发送，避免网络层误序列化后泄露手牌。
 */
public record RoomExecutionResult(
        boolean duplicate,
        boolean ignored,
        long connectionEpoch,
        long lastSequence,
        List<GameEvent> events,
        GameSnapshot requesterSnapshot
) {
    public RoomExecutionResult {
        events = events == null ? List.of() : List.copyOf(events);
    }
}
