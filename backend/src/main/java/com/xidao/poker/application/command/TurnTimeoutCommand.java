package com.xidao.poker.application.command;

/** 定时器只能提交带 handId/turnId 的命令，过期任务不会影响后续回合。 */
public record TurnTimeoutCommand(
        String roomId,
        String commandId,
        String playerId,
        long handId,
        long turnId
) {
    public TurnTimeoutCommand {
        if (roomId == null || roomId.isBlank()) throw new IllegalArgumentException("room id is required");
        if (commandId == null || commandId.isBlank()) throw new IllegalArgumentException("command id is required");
        if (playerId == null || playerId.isBlank()) throw new IllegalArgumentException("player id is required");
        if (handId <= 0 || turnId <= 0) throw new IllegalArgumentException("hand and turn ids must be positive");
    }
}
