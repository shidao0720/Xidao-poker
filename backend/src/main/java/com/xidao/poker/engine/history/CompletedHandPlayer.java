package com.xidao.poker.engine.history;

import com.xidao.poker.engine.card.Card;

import java.util.List;

/** 完成手牌中的玩家结果，包含不会通过普通查看者 Snapshot 暴露的私有牌。 */
public record CompletedHandPlayer(
        String playerId,
        String playerName,
        int seat,
        int startingStack,
        int endingStack,
        int totalContribution,
        int winnings,
        boolean folded,
        boolean disconnected,
        boolean busted,
        boolean showdown,
        Long handKey,
        String handCategory,
        List<Card> holeCards
) {
    public CompletedHandPlayer {
        if (playerId == null || playerId.isBlank()) throw new IllegalArgumentException("player id is required");
        if (playerName == null || playerName.isBlank()) throw new IllegalArgumentException("player name is required");
        if (seat < 0 || seat > 9) throw new IllegalArgumentException("seat must be between 0 and 9");
        if (startingStack < 0 || endingStack < 0 || totalContribution < 0 || winnings < 0) {
            throw new IllegalArgumentException("chip amounts cannot be negative");
        }
        if (showdown && (handKey == null || handCategory == null || handCategory.isBlank())) {
            throw new IllegalArgumentException("showdown result must contain both hand key and category");
        }
        if (!showdown && (handKey != null || handCategory != null)) {
            throw new IllegalArgumentException("non-showdown player cannot contain a hand result");
        }
        holeCards = holeCards == null ? List.of() : List.copyOf(holeCards);
        if (holeCards.size() != 2) throw new IllegalArgumentException("completed participant must have two hole cards");
    }
}
