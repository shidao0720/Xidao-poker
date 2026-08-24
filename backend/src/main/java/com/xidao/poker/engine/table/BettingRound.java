package com.xidao.poker.engine.table;

import com.xidao.poker.engine.action.ActionType;
import com.xidao.poker.engine.action.ActionErrorCode;
import com.xidao.poker.engine.action.ActionValidator;
import com.xidao.poker.engine.action.IllegalActionException;
import com.xidao.poker.engine.action.PlayerAction;
import com.xidao.poker.engine.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** 单个 street 的无限注下注控制器。 */
public final class BettingRound {
    private final List<Player> players;
    private final int maxSeats;
    private int currentBet;
    private int minRaise;
    private Integer actorSeat;
    private Integer lastAggressorSeat;

    public BettingRound(List<Player> players, int firstActorSeat, int currentBet, int minRaise, int maxSeats) {
        if (players == null || players.size() < 2) throw new IllegalArgumentException("at least two players required");
        if (minRaise <= 0) throw new IllegalArgumentException("minimum raise must be positive");
        this.players = new ArrayList<>(players);
        this.players.sort(Comparator.comparingInt(Player::seat));
        this.currentBet = currentBet;
        this.minRaise = minRaise;
        this.maxSeats = maxSeats;
        this.actorSeat = findAtOrAfter(firstActorSeat);
    }

    public BettingActionResult act(PlayerAction action) {
        Player player = requireActor(action.playerId());
        ActionType type = action.type();
        int toCall = Math.max(0, currentBet - player.streetBet());
        ActionValidator.validateBet(player, action, currentBet, minRaise);

        int paid = 0;
        boolean fullRaise = false;
        switch (type) {
            case FOLD -> player.fold();
            case CHECK -> player.setHasActed(true);
            case CALL -> {
                paid = player.contribute(toCall);
                player.setHasActed(true);
            }
            case BET, RAISE -> {
                int previousBet = currentBet;
                paid = player.contribute(action.amount() - player.streetBet());
                int raiseBy = action.amount() - previousBet;
                currentBet = action.amount();
                minRaise = raiseBy;
                lastAggressorSeat = player.seat();
                resetActionAfterFullRaise(player);
                fullRaise = true;
            }
            case ALL_IN -> {
                int target = player.streetBet() + player.stack();
                paid = player.contribute(player.stack());
                player.setHasActed(true);
                if (target > currentBet) {
                    int raiseBy = target - currentBet;
                    currentBet = target;
                    if (raiseBy >= minRaise) {
                        minRaise = raiseBy;
                        lastAggressorSeat = player.seat();
                        resetActionAfterFullRaise(player);
                        fullRaise = true;
                    }
                }
            }
        }

        boolean complete = isComplete();
        actorSeat = complete ? null : findNextRequiringAction(player.seat());
        if (!complete && actorSeat == null) {
            throw new IllegalStateException("betting round has no eligible next actor");
        }
        return new BettingActionResult(player.id(), type, paid, currentBet, fullRaise, complete, actorSeat);
    }

    public Set<ActionType> legalActions(String playerId) {
        Player player = findPlayer(playerId);
        if (actorSeat == null || player.seat() != actorSeat || !player.canAct()) {
            return Set.of();
        }
        int toCall = Math.max(0, currentBet - player.streetBet());
        EnumSet<ActionType> actions = EnumSet.of(ActionType.ALL_IN);
        if (toCall == 0) {
            actions.add(ActionType.CHECK);
            if (currentBet == 0 && player.stack() > 0) actions.add(ActionType.BET);
        } else {
            actions.add(ActionType.FOLD);
            actions.add(ActionType.CALL);
            // short all-in 不会重新开放已行动玩家的加注权。
            if (!player.hasActed() && player.streetBet() + player.stack() > currentBet) {
                actions.add(ActionType.RAISE);
            }
        }
        return Set.copyOf(actions);
    }

    public boolean isComplete() {
        long alive = players.stream().filter(Player::isInHand).count();
        if (alive <= 1) return true;
        for (Player player : players) {
            if (!player.canAct()) continue;
            if (!player.hasActed() || player.streetBet() != currentBet) return false;
        }
        return true;
    }

    private Player requireActor(String playerId) {
        Player player = findPlayer(playerId);
        ActionValidator.validateTurn(player, actorSeat);
        return player;
    }

    private Player findPlayer(String playerId) {
        return players.stream().filter(p -> p.id().equals(playerId)).findFirst()
                .orElseThrow(() -> new IllegalActionException(ActionErrorCode.UNKNOWN_PLAYER, "unknown player"));
    }

    private void resetActionAfterFullRaise(Player aggressor) {
        for (Player player : players) {
            if (player.canAct()) player.setHasActed(player == aggressor);
        }
    }

    private Integer findAtOrAfter(int seat) {
        for (int offset = 0; offset < maxSeats; offset++) {
            int candidate = Math.floorMod(seat + offset, maxSeats);
            Player player = playerAt(candidate);
            if (player != null && player.canAct()) return candidate;
        }
        return null;
    }

    private Integer findNextRequiringAction(int afterSeat) {
        for (int offset = 1; offset <= maxSeats; offset++) {
            int candidate = Math.floorMod(afterSeat + offset, maxSeats);
            Player player = playerAt(candidate);
            if (player == null || !player.canAct()) continue;
            if (!player.hasActed() || player.streetBet() < currentBet) return candidate;
        }
        return null;
    }

    private Player playerAt(int seat) {
        return players.stream().filter(p -> p.seat() == seat).findFirst().orElse(null);
    }

    public int currentBet() { return currentBet; }
    public int minRaise() { return minRaise; }
    public Integer actorSeat() { return actorSeat; }
    public Integer lastAggressorSeat() { return lastAggressorSeat; }
}
