package com.xidao.poker.engine.history;

import com.xidao.poker.engine.action.ActionType;
import com.xidao.poker.engine.game.GamePhase;

/** 一手牌内已被引擎接受的行动；只用于服务端历史归档，不参与网络广播。 */
public record CompletedHandAction(
        int actionIndex,
        long turnId,
        String playerId,
        GamePhase phase,
        ActionType action,
        int paid,
        int stackAfter,
        int streetBetAfter,
        int currentBetAfter,
        boolean fullRaise
) {
    public CompletedHandAction {
        if (actionIndex <= 0) throw new IllegalArgumentException("action index must be positive");
        if (turnId <= 0) throw new IllegalArgumentException("turn id must be positive");
        if (playerId == null || playerId.isBlank()) throw new IllegalArgumentException("player id is required");
        if (phase == null || !phase.isBettingPhase()) {
            throw new IllegalArgumentException("action phase must be a betting phase");
        }
        if (action == null) throw new IllegalArgumentException("action type is required");
        if (paid < 0 || stackAfter < 0 || streetBetAfter < 0 || currentBetAfter < 0) {
            throw new IllegalArgumentException("chip amounts cannot be negative");
        }
    }
}
