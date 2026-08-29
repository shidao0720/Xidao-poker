package com.xidao.poker.application.command;

/** 断线宽限到期命令；epoch 是其业务陈旧判据，hand/turn 用于完整记录调度上下文。 */
public record DisconnectExpiryCommand(
        String roomId,
        String commandId,
        String playerId,
        long connectionEpoch,
        long handId,
        long turnId
) implements RoomTimerCommand {
    public DisconnectExpiryCommand {
        if (roomId == null || roomId.isBlank()) throw new IllegalArgumentException("room id is required");
        if (commandId == null || commandId.isBlank()) throw new IllegalArgumentException("command id is required");
        if (playerId == null || playerId.isBlank()) throw new IllegalArgumentException("player id is required");
        if (connectionEpoch <= 0) throw new IllegalArgumentException("connection epoch must be positive");
        if (handId < 0 || turnId < 0) throw new IllegalArgumentException("hand and turn ids cannot be negative");
    }
}
