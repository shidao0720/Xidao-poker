package com.xidao.poker.application.room;

/**
 * 由 WebSocket 基础设施实现；调用时已经离开房间锁。Snapshot 只能发送给信封中
 * 完全匹配的 connectionId + epoch，目标连接已被替换时应直接丢弃。
 */
@FunctionalInterface
public interface RoomDeliverySink {
    void deliver(String roomId, RoomDelivery delivery);
}
