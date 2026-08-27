package com.xidao.poker.test;

import com.xidao.poker.application.history.CompletedHandArchive;
import com.xidao.poker.engine.action.ActionType;
import com.xidao.poker.engine.card.Card;
import com.xidao.poker.engine.card.Rank;
import com.xidao.poker.engine.card.Suit;
import com.xidao.poker.engine.game.GameConfig;
import com.xidao.poker.engine.game.GamePhase;
import com.xidao.poker.engine.history.CompletedHandAction;
import com.xidao.poker.engine.history.CompletedHandPlayer;
import com.xidao.poker.engine.history.CompletedHandSnapshot;
import com.xidao.poker.engine.pot.Pot;
import com.xidao.poker.engine.pot.PotAward;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class HistoryFixtures {
    private HistoryFixtures() {
    }

    public static CompletedHandArchive archive(long handId) {
        Instant started = Instant.parse("2026-08-27T01:00:00Z").plusSeconds(handId);
        CompletedHandPlayer alice = new CompletedHandPlayer(
                "A", "Alice", 0, 100, 95, 5, 0,
                true, false, false, false, null, null,
                List.of(new Card(Rank.ACE, Suit.SPADES), new Card(Rank.KING, Suit.SPADES))
        );
        CompletedHandPlayer bob = new CompletedHandPlayer(
                "B", "Bob", 1, 100, 105, 10, 15,
                false, false, false, false, null, null,
                List.of(new Card(Rank.QUEEN, Suit.HEARTS), new Card(Rank.JACK, Suit.HEARTS))
        );
        CompletedHandSnapshot hand = new CompletedHandSnapshot(
                "history-game",
                handId,
                new GameConfig(5, 10, 100, 2),
                0,
                0,
                1,
                15,
                List.of(),
                List.of(new Pot(15, List.of("B"))),
                List.of(new PotAward(15, Map.of("B", 15))),
                List.of(alice, bob),
                List.of(new CompletedHandAction(
                        1, 1, "A", GamePhase.PREFLOP, ActionType.FOLD,
                        0, 95, 5, 10, false
                )),
                true
        );
        return new CompletedHandArchive(
                "History Room",
                Instant.parse("2026-08-27T00:00:00Z"),
                started,
                started.plusSeconds(30),
                hand
        );
    }
}
