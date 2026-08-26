package com.xidao.poker.application.command;

/** 开始下一手必须绑定客户端所见的上一手，防止延迟命令误开后续手牌。 */
public record StartGameCommand(
        String roomId,
        String commandId,
        String playerId,
        String connectionId,
        long connectionEpoch,
        long expectedPreviousHandId
) {
    public StartGameCommand {
        if (roomId == null || roomId.isBlank()) throw new IllegalArgumentException("room id is required");
        if (commandId == null || commandId.isBlank()) throw new IllegalArgumentException("command id is required");
        if (playerId == null || playerId.isBlank()) throw new IllegalArgumentException("player id is required");
        if (connectionId == null || connectionId.isBlank()) throw new IllegalArgumentException("connection id is required");
        if (connectionEpoch <= 0) throw new IllegalArgumentException("connection epoch must be positive");
        if (expectedPreviousHandId < 0) throw new IllegalArgumentException("previous hand id cannot be negative");
    }
}
