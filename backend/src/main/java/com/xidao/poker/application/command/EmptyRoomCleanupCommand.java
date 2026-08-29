package com.xidao.poker.application.command;

import java.time.Instant;

/** 空房清理命令绑定观察到的空置起点；重新加入会使旧命令自动失效。 */
public record EmptyRoomCleanupCommand(
        String roomId,
        String commandId,
        long handId,
        long turnId,
        Instant expectedEmptySince,
        Instant expiresAt
) implements RoomTimerCommand {
    public EmptyRoomCleanupCommand {
        if (roomId == null || roomId.isBlank()) throw new IllegalArgumentException("room id is required");
        if (commandId == null || commandId.isBlank()) throw new IllegalArgumentException("command id is required");
        if (handId < 0 || turnId < 0) throw new IllegalArgumentException("hand and turn ids cannot be negative");
        if (expectedEmptySince == null || expiresAt == null) {
            throw new IllegalArgumentException("empty-room timer timestamps are required");
        }
        if (expiresAt.isBefore(expectedEmptySince)) {
            throw new IllegalArgumentException("expiry cannot precede empty-since");
        }
    }
}
