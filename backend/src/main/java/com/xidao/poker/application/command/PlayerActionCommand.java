package com.xidao.poker.application.command;

import com.xidao.poker.engine.action.ActionType;
import com.xidao.poker.engine.action.PlayerAction;

/** 客户端行动信封。playerId 必须由已认证连接绑定关系提供，而不是信任消息体。 */
public record PlayerActionCommand(
        String roomId,
        String commandId,
        String playerId,
        String connectionId,
        long connectionEpoch,
        long handId,
        long turnId,
        ActionType action,
        int amount
) {
    public PlayerActionCommand {
        requireText(roomId, "room id");
        requireText(commandId, "command id");
        requireText(playerId, "player id");
        requireText(connectionId, "connection id");
        if (connectionEpoch <= 0) throw new IllegalArgumentException("connection epoch must be positive");
        if (handId <= 0) throw new IllegalArgumentException("hand id must be positive");
        if (turnId <= 0) throw new IllegalArgumentException("turn id must be positive");
        if (action == null) throw new IllegalArgumentException("action is required");
        boolean requiresAmount = action == ActionType.BET || action == ActionType.RAISE;
        if (requiresAmount && amount <= 0) throw new IllegalArgumentException("bet/raise amount must be positive");
        if (!requiresAmount && amount != 0) throw new IllegalArgumentException("this action does not accept an amount");
    }

    public PlayerAction toPlayerAction() {
        return switch (action) {
            case FOLD -> PlayerAction.fold(playerId);
            case CHECK -> PlayerAction.check(playerId);
            case CALL -> PlayerAction.call(playerId);
            case BET -> PlayerAction.bet(playerId, amount);
            case RAISE -> PlayerAction.raiseTo(playerId, amount);
            case ALL_IN -> PlayerAction.allIn(playerId);
        };
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }
}
