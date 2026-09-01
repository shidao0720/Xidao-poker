package com.xidao.poker.engine.snapshot;

import com.xidao.poker.engine.card.Card;
import com.xidao.poker.engine.player.PlayerStatus;
import com.xidao.poker.engine.player.ConnectionStatus;
import com.xidao.poker.engine.player.HandStatus;
import com.xidao.poker.engine.player.SeatStatus;

import java.util.List;
import java.util.Map;

public record PlayerSnapshot(
        String id,
        String name,
        int seat,
        int stack,
        int streetBet,
        int totalContribution,
        PlayerStatus status,
        ConnectionStatus connectionStatus,
        SeatStatus seatStatus,
        HandStatus handStatus,
        boolean inHand,
        boolean canAct,
        boolean ready,
        List<Card> holeCards,
        String avatarKey,
        Map<String, String> cosmetics
) {
    public PlayerSnapshot(
            String id, String name, int seat, int stack, int streetBet, int totalContribution,
            PlayerStatus status, ConnectionStatus connectionStatus, SeatStatus seatStatus,
            HandStatus handStatus, boolean inHand, boolean canAct, boolean ready, List<Card> holeCards
    ) {
        this(id, name, seat, stack, streetBet, totalContribution, status, connectionStatus,
                seatStatus, handStatus, inHand, canAct, ready, holeCards, "default", Map.of());
    }

    public PlayerSnapshot {
        holeCards = holeCards == null ? List.of() : List.copyOf(holeCards);
        avatarKey = avatarKey == null || avatarKey.isBlank() ? "default" : avatarKey;
        cosmetics = cosmetics == null ? Map.of() : Map.copyOf(cosmetics);
    }
}
