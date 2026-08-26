package com.xidao.poker.engine.action;

/**
 * 客户端提交的行动意图。BET 和 RAISE 的 amount 表示该街目标总下注额。
 */
public record PlayerAction(String playerId, ActionType type, int amount) {
    public PlayerAction {
        if (playerId == null || playerId.isBlank()) throw new IllegalArgumentException("player id is required");
        if (type == null) throw new IllegalArgumentException("action type is required");
        boolean requiresAmount = type == ActionType.BET || type == ActionType.RAISE;
        if (requiresAmount && amount <= 0) throw new IllegalArgumentException("bet/raise amount must be positive");
        if (!requiresAmount && amount != 0) throw new IllegalArgumentException("this action does not accept an amount");
    }

    public static PlayerAction fold(String id) { return new PlayerAction(id, ActionType.FOLD, 0); }
    public static PlayerAction check(String id) { return new PlayerAction(id, ActionType.CHECK, 0); }
    public static PlayerAction call(String id) { return new PlayerAction(id, ActionType.CALL, 0); }
    public static PlayerAction bet(String id, int amount) { return new PlayerAction(id, ActionType.BET, amount); }
    public static PlayerAction raiseTo(String id, int amount) { return new PlayerAction(id, ActionType.RAISE, amount); }
    public static PlayerAction allIn(String id) { return new PlayerAction(id, ActionType.ALL_IN, 0); }
}
