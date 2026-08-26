package com.xidao.poker.engine.snapshot;

import com.xidao.poker.engine.card.Card;
import com.xidao.poker.engine.player.PlayerStatus;

import java.util.List;

public record PlayerSnapshot(
        String id,
        String name,
        int seat,
        int stack,
        int streetBet,
        PlayerStatus status,
        boolean ready,
        List<Card> holeCards
) {
    public PlayerSnapshot {
        holeCards = holeCards == null ? List.of() : List.copyOf(holeCards);
    }
}
