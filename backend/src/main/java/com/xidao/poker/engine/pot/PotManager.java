package com.xidao.poker.engine.pot;

import com.xidao.poker.engine.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 根据整手牌累计投入构造主池和边池，并执行确定性的平局分配。 */
public final class PotManager {
    private PotManager() {
    }

    public static List<Pot> buildPots(List<Player> players) {
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
            int amount = (level - previous) * layer.size();
            List<String> eligible = layer.stream()
                    .filter(Player::isInHand)
                    .map(Player::id)
                    .toList();
            // 正常下注不会产生无人有资格争夺的池；强制掉线弃牌可能产生这种边界。
            // 此时该层作为 dead money，由仍留在本手牌中的玩家争夺，避免筹码丢失或卡局。
            if (eligible.isEmpty()) eligible = survivingPlayerIds;
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
        Map<String, Player> byId = new LinkedHashMap<>();
        players.forEach(p -> byId.put(p.id(), p));
        List<PotAward> awards = new ArrayList<>();

        for (Pot pot : pots) {
            if (pot.eligiblePlayerIds().isEmpty()) {
                throw new IllegalStateException("pot has no eligible players");
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
                winner.addWinnings(amount);
                winnings.put(winner.id(), amount);
            }
            awards.add(new PotAward(pot.amount(), winnings));
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
