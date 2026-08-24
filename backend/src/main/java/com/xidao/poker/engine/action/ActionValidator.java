package com.xidao.poker.engine.action;

import com.xidao.poker.engine.player.Player;

/** 集中校验下注意图，WebSocket 层不得绕过这里直接修改游戏状态。 */
public final class ActionValidator {
    private ActionValidator() {
    }

    public static void validateTurn(Player player, Integer actorSeat) {
        if (actorSeat == null || player.seat() != actorSeat) {
            reject(ActionErrorCode.NOT_YOUR_TURN, "not this player's turn");
        }
        if (!player.canAct()) {
            reject(ActionErrorCode.INVALID_ACTION, "player cannot act in current status");
        }
    }

    public static void validateBet(Player player, PlayerAction action, int currentBet, int minRaise) {
        int toCall = Math.max(0, currentBet - player.streetBet());
        int target = action.amount();
        switch (action.type()) {
            case FOLD -> {
                if (toCall == 0) reject(ActionErrorCode.INVALID_ACTION, "cannot fold when check is available");
            }
            case CHECK -> {
                if (toCall != 0) reject(ActionErrorCode.INVALID_ACTION, "cannot check while facing a bet");
            }
            case CALL -> {
                if (toCall == 0) reject(ActionErrorCode.INVALID_ACTION, "nothing to call");
            }
            case BET -> {
                if (currentBet != 0) reject(ActionErrorCode.INVALID_ACTION, "use raise when a bet exists");
                validateTarget(player, target);
                if (target < minRaise && target != player.streetBet() + player.stack()) {
                    reject(ActionErrorCode.INVALID_AMOUNT, "bet is below minimum");
                }
            }
            case RAISE -> {
                if (currentBet == 0) reject(ActionErrorCode.INVALID_ACTION, "use bet when no bet exists");
                if (player.hasActed()) {
                    reject(ActionErrorCode.INVALID_ACTION, "raising is not reopened after a short all-in");
                }
                validateTarget(player, target);
                if (target <= currentBet) reject(ActionErrorCode.INVALID_AMOUNT, "raise must exceed current bet");
                int raiseBy = target - currentBet;
                boolean allInTarget = target == player.streetBet() + player.stack();
                if (raiseBy < minRaise && !allInTarget) {
                    reject(ActionErrorCode.INVALID_AMOUNT, "raise is below minimum");
                }
            }
            case ALL_IN -> {
                if (player.stack() <= 0) reject(ActionErrorCode.INSUFFICIENT_CHIPS, "player has no chips");
            }
        }
    }

    private static void validateTarget(Player player, int target) {
        if (target <= player.streetBet()) reject(ActionErrorCode.INVALID_AMOUNT, "target must add chips");
        if (target > player.streetBet() + player.stack()) {
            reject(ActionErrorCode.INSUFFICIENT_CHIPS, "target exceeds stack");
        }
    }

    private static void reject(ActionErrorCode code, String message) {
        throw new IllegalActionException(code, message);
    }
}
