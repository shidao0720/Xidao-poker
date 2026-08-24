package com.xidao.poker.engine.table;

import com.xidao.poker.engine.action.ActionType;

public record BettingActionResult(
        String playerId,
        ActionType action,
        int paid,
        int currentBet,
        boolean fullRaise,
        boolean roundComplete,
        Integer nextActorSeat
) {
}
