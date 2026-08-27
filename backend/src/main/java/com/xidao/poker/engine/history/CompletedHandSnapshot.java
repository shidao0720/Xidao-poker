package com.xidao.poker.engine.history;

import com.xidao.poker.engine.card.Card;
import com.xidao.poker.engine.game.GameConfig;
import com.xidao.poker.engine.pot.Pot;
import com.xidao.poker.engine.pot.PotAward;

import java.util.List;

/**
 * 已结算手牌的服务端内部投影。它是持久化边界，不得放入 WebSocket DTO。
 */
public record CompletedHandSnapshot(
        String sessionId,
        long handId,
        GameConfig config,
        int buttonSeat,
        int smallBlindSeat,
        int bigBlindSeat,
        int totalPot,
        List<Card> communityCards,
        List<Pot> pots,
        List<PotAward> awards,
        List<CompletedHandPlayer> players,
        List<CompletedHandAction> actions,
        boolean actionHistoryComplete
) {
    public CompletedHandSnapshot {
        if (sessionId == null || sessionId.isBlank()) throw new IllegalArgumentException("session id is required");
        if (handId <= 0) throw new IllegalArgumentException("hand id must be positive");
        if (config == null) throw new IllegalArgumentException("game config is required");
        if (buttonSeat < 0 || smallBlindSeat < 0 || bigBlindSeat < 0) {
            throw new IllegalArgumentException("table seats cannot be negative");
        }
        if (totalPot <= 0) throw new IllegalArgumentException("total pot must be positive");
        communityCards = communityCards == null ? List.of() : List.copyOf(communityCards);
        pots = pots == null ? List.of() : List.copyOf(pots);
        awards = awards == null ? List.of() : List.copyOf(awards);
        players = players == null ? List.of() : List.copyOf(players);
        actions = actions == null ? List.of() : List.copyOf(actions);
        if (players.size() < 2 || players.size() > config.maxPlayers()) {
            throw new IllegalArgumentException("completed hand must contain 2..maxPlayers participants");
        }
        int awarded = awards.stream().mapToInt(PotAward::potAmount).sum();
        if (awarded != totalPot) throw new IllegalArgumentException("awards must distribute the total pot");
    }
}
