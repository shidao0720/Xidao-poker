package com.xidao.poker.engine.table;

import com.xidao.poker.engine.action.ActionType;
import com.xidao.poker.engine.action.ActionErrorCode;
import com.xidao.poker.engine.action.ActionValidator;
import com.xidao.poker.engine.action.IllegalActionException;
import com.xidao.poker.engine.action.PlayerAction;
import com.xidao.poker.engine.player.Player;
import com.xidao.poker.engine.snapshot.ActionOptions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 单个 street 的无限注下注控制器。 */
public final class BettingRound {
    private final List<Player> players;
    private final int maxSeats;
    private final Map<String, Integer> actedAtBet = new HashMap<>();
    private int currentBet;
    private int minRaise;
    private Integer actorSeat;
    private Integer lastAggressorSeat;

    public BettingRound(List<Player> players, int firstActorSeat, int currentBet, int minRaise, int maxSeats) {
        if (players == null || players.stream().anyMatch(java.util.Objects::isNull)
                || players.stream().filter(Player::isInHand).count() < 2) {
            throw new IllegalArgumentException("at least two hand participants required");
        }
        if (maxSeats < 2 || maxSeats > 10) throw new IllegalArgumentException("max seats must be 2..10");
        if (firstActorSeat < 0 || firstActorSeat >= maxSeats) {
            throw new IllegalArgumentException("first actor seat must belong to the table");
        }
        if (currentBet < 0) throw new IllegalArgumentException("current bet cannot be negative");
        if (minRaise <= 0) throw new IllegalArgumentException("minimum raise must be positive");
        if (players.stream().map(Player::id).distinct().count() != players.size()
                || players.stream().map(Player::seat).distinct().count() != players.size()) {
            throw new IllegalArgumentException("players must have distinct ids and seats");
        }
        if (players.stream().anyMatch(player -> player.seat() >= maxSeats)) {
            throw new IllegalArgumentException("player seat is outside the table");
        }
        this.players = new ArrayList<>(players);
        this.players.sort(Comparator.comparingInt(Player::seat));
        this.currentBet = currentBet;
        this.minRaise = minRaise;
        this.maxSeats = maxSeats;
        this.actorSeat = findAtOrAfter(firstActorSeat);
        if (isComplete()) this.actorSeat = null;
    }

    public BettingActionResult act(PlayerAction action) {
        Player player = requireActor(action.playerId());
        ActionType type = action.type();
        int toCall = Math.max(0, currentBet - player.streetBet());
        ActionValidator.validateBet(player, action, currentBet, minRaise, isRaiseReopenedFor(player));

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
        if (type != ActionType.FOLD) actedAtBet.put(player.id(), currentBet);

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
        EnumSet<ActionType> actions = EnumSet.noneOf(ActionType.class);
        int allInTarget = player.streetBet() + player.stack();
        boolean raiseReopened = isRaiseReopenedFor(player);
        if (raiseReopened || allInTarget <= currentBet) actions.add(ActionType.ALL_IN);
        if (toCall == 0) {
            actions.add(ActionType.CHECK);
            if (currentBet == 0 && allInTarget >= minRaise) actions.add(ActionType.BET);
        } else {
            actions.add(ActionType.FOLD);
            actions.add(ActionType.CALL);
            if (raiseReopened && allInTarget - currentBet >= minRaise) {
                actions.add(ActionType.RAISE);
            }
        }
        return Set.copyOf(actions);
    }

    public ActionOptions actionOptions(String playerId) {
        Player player = findPlayer(playerId);
        Set<ActionType> actions = legalActions(playerId);
        if (actions.isEmpty()) return ActionOptions.none();

        int toCall = Math.max(0, currentBet - player.streetBet());
        int maximumTo = Math.addExact(player.streetBet(), player.stack());
        Integer minimumBetTo = actions.contains(ActionType.BET) ? minRaise : null;
        Integer minimumRaiseTo = actions.contains(ActionType.RAISE)
                ? Math.addExact(currentBet, minRaise)
                : null;
        return new ActionOptions(
                actions,
                toCall,
                Math.min(toCall, player.stack()),
                minimumBetTo,
                minimumRaiseTo,
                maximumTo
        );
    }

    public boolean isComplete() {
        long alive = players.stream().filter(Player::isInHand).count();
        if (alive <= 1) return true;
        List<Player> canActPlayers = players.stream().filter(Player::canAct).toList();
        if (canActPlayers.isEmpty()) return true;
        // 其余未弃牌玩家都已 All-in 时，唯一可行动者若已跟平，无需执行无意义的 Check。
        if (canActPlayers.size() == 1) {
            return canActPlayers.getFirst().streetBet() == currentBet;
        }
        for (Player player : players) {
            if (!player.canAct()) continue;
            if (!player.hasActed() || player.streetBet() != currentBet) return false;
        }
        return true;
    }

    /**
     * 掉线、破产或转观察者等资格变化后重新计算行动指针。
     * 返回 true 表示该下注街已经结束，调用者应立即推进状态机。
     */
    public boolean reconcileAfterEligibilityChange(int changedSeat) {
        if (isComplete()) {
            actorSeat = null;
            return true;
        }
        Player current = actorSeat == null ? null : playerAt(actorSeat);
        if (current != null && current.canAct() && requiresAction(current)) {
            return false;
        }
        actorSeat = findNextRequiringAction(changedSeat);
        if (actorSeat == null) {
            throw new IllegalStateException("incomplete betting round has no eligible actor");
        }
        return false;
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
            if (!player.canAct()) continue;
            boolean isAggressor = player == aggressor;
            player.setHasActed(isAggressor);
            if (!isAggressor) actedAtBet.remove(player.id());
        }
    }

    /**
     * 单次短码加注不重新开放加注；多个短码加注累计达到完整加注额时重新开放。
     */
    private boolean isRaiseReopenedFor(Player player) {
        if (!player.hasActed()) return true;
        Integer previousActionBet = actedAtBet.get(player.id());
        return previousActionBet != null && currentBet - previousActionBet >= minRaise;
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
            if (requiresAction(player)) return candidate;
        }
        return null;
    }

    private boolean requiresAction(Player player) {
        return player.canAct() && (!player.hasActed() || player.streetBet() < currentBet);
    }

    private Player playerAt(int seat) {
        return players.stream().filter(p -> p.seat() == seat).findFirst().orElse(null);
    }

    public int currentBet() { return currentBet; }
    public int minRaise() { return minRaise; }
    public Integer actorSeat() { return actorSeat; }
    public Integer lastAggressorSeat() { return lastAggressorSeat; }
}
