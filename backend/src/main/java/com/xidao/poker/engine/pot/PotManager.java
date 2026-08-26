package com.xidao.poker.engine.pot;

import com.xidao.poker.engine.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 根据整手牌累计投入构造主池和边池，并执行确定性的平局分配。 */
public final class PotManager {
    private PotManager() {
    }

    public static List<Pot> buildPots(List<Player> players) {
        Objects.requireNonNull(players, "players");
        if (players.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("player is required");
        if (players.stream().map(Player::id).distinct().count() != players.size()) {
            throw new IllegalArgumentException("players cannot contain duplicate ids");
        }
        List<String> survivingPlayerIds = players.stream()
                .filter(Player::isInHand)
                .map(Player::id)
                .toList();
        List<Integer> levels = players.stream()
                .map(Player::totalContribution)
                .filter(v -> v > 0)
                .distinct()
                .sorted()
                .toList();
        List<Pot> pots = new ArrayList<>();
        int previous = 0;
        for (int level : levels) {
            List<Player> layer = players.stream()
                    .filter(p -> p.totalContribution() >= level)
                    .toList();
            int amount = Math.multiplyExact(level - previous, layer.size());
            List<String> eligible = layer.stream()
                    .filter(Player::isInHand)
                    .map(Player::id)
                    .toList();
            // 正常下注不会产生无人有资格争夺的池；强制掉线弃牌可能产生这种边界。
            // 此时该层作为 dead money，由仍留在本手牌中的玩家争夺，避免筹码丢失或卡局。
            if (eligible.isEmpty()) eligible = survivingPlayerIds;
            if (eligible.isEmpty()) throw new IllegalStateException("contributed chips have no remaining contender");
            if (amount > 0) pots.add(new Pot(amount, eligible));
            previous = level;
        }
        return List.copyOf(pots);
    }

    public static List<PotAward> settle(
            List<Pot> pots,
            List<Player> players,
            Map<String, Long> handKeys,
            int buttonSeat,
            int maxSeats
    ) {
        Objects.requireNonNull(pots, "pots");
        Objects.requireNonNull(players, "players");
        Objects.requireNonNull(handKeys, "hand keys");
        if (maxSeats < 2 || maxSeats > 10) throw new IllegalArgumentException("max seats must be 2..10");
        if (buttonSeat < 0 || buttonSeat >= maxSeats) {
            throw new IllegalArgumentException("button seat must belong to the table");
        }
        Map<String, Player> byId = new LinkedHashMap<>();
        java.util.Set<Integer> seats = new java.util.HashSet<>();
        for (Player player : players) {
            if (player == null) throw new IllegalArgumentException("player is required");
            if (byId.putIfAbsent(player.id(), player) != null) {
                throw new IllegalArgumentException("players cannot contain duplicate ids");
            }
            if (player.seat() >= maxSeats || !seats.add(player.seat())) {
                throw new IllegalArgumentException("players must occupy distinct table seats");
            }
        }
        int contributed = 0;
        for (Player player : players) contributed = Math.addExact(contributed, player.totalContribution());
        int representedByPots = 0;
        for (Pot pot : pots) {
            Objects.requireNonNull(pot, "pot");
            representedByPots = Math.addExact(representedByPots, pot.amount());
        }
        if (representedByPots != contributed) {
            throw new IllegalArgumentException("pot amounts must equal total player contributions");
        }
        List<PotAward> awards = new ArrayList<>();

        for (Pot pot : pots) {
            for (String id : pot.eligiblePlayerIds()) {
                if (!byId.containsKey(id)) throw new IllegalArgumentException("unknown eligible player " + id);
                if (!byId.get(id).isInHand()) {
                    throw new IllegalArgumentException("ineligible folded or busted player " + id);
                }
            }
            long best = pot.eligiblePlayerIds().stream()
                    .mapToLong(id -> requireHandKey(handKeys, id))
                    .max().orElseThrow();
            List<Player> winners = pot.eligiblePlayerIds().stream()
                    .filter(id -> requireHandKey(handKeys, id) == best)
                    .map(byId::get)
                    .sorted(Comparator.comparingInt(p -> clockwiseDistance(buttonSeat, p.seat(), maxSeats)))
                    .toList();
            int share = pot.amount() / winners.size();
            int remainder = pot.amount() % winners.size();
            Map<String, Integer> winnings = new LinkedHashMap<>();
            for (int i = 0; i < winners.size(); i++) {
                Player winner = winners.get(i);
                int amount = share + (i < remainder ? 1 : 0);
                winnings.put(winner.id(), amount);
            }
            awards.add(new PotAward(pot.amount(), winnings));
        }

        // 所有奖池、牌力和玩家引用验证完成后再统一修改筹码，避免半结算状态。
        for (PotAward award : awards) {
            for (Map.Entry<String, Integer> winning : award.winnings().entrySet()) {
                byId.get(winning.getKey()).addWinnings(winning.getValue());
            }
        }
        return List.copyOf(awards);
    }

    private static long requireHandKey(Map<String, Long> keys, String id) {
        Long key = keys.get(id);
        if (key == null) throw new IllegalArgumentException("missing hand key for " + id);
        return key;
    }

    private static int clockwiseDistance(int buttonSeat, int seat, int maxSeats) {
        return Math.floorMod(seat - buttonSeat - 1, maxSeats);
    }
}
