package com.xidao.poker.engine.pot;

import com.xidao.poker.engine.player.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PotManagerTest {
    @Test
    void buildsMainAndSidePotFromCumulativeContributions() {
        Player a = player("A", 0, 500);
        Player b = player("B", 1, 1_000);
        Player c = player("C", 2, 1_000);
        a.contribute(500);
        b.contribute(1_000);
        c.contribute(1_000);

        assertThat(PotManager.buildPots(List.of(a, b, c))).containsExactly(
                new Pot(1_500, List.of("A", "B", "C")),
                new Pot(1_000, List.of("B", "C"))
        );
    }

    @Test
    void foldedMoneyStaysInPotButPlayerIsNotEligible() {
        Player a = player("A", 0, 1_000);
        Player b = player("B", 1, 1_000);
        Player c = player("C", 2, 1_000);
        a.contribute(500);
        b.contribute(500);
        c.contribute(500);
        c.fold();

        assertThat(PotManager.buildPots(List.of(a, b, c)))
                .containsExactly(new Pot(1_500, List.of("A", "B")));
    }

    @Test
    void strongestShortStackWinsOnlyMainPot() {
        Player a = player("A", 0, 500);
        Player b = player("B", 1, 1_000);
        Player c = player("C", 2, 1_000);
        a.contribute(500);
        b.contribute(1_000);
        c.contribute(1_000);

        List<PotAward> awards = PotManager.settle(
                PotManager.buildPots(List.of(a, b, c)),
                List.of(a, b, c), Map.of("A", 30L, "B", 20L, "C", 10L), 2, 10);

        assertThat(awards.get(0).winnings()).containsExactlyEntriesOf(Map.of("A", 1_500));
        assertThat(awards.get(1).winnings()).containsExactlyEntriesOf(Map.of("B", 1_000));
        assertThat(a.stack()).isEqualTo(1_500);
        assertThat(b.stack()).isEqualTo(1_000);
        assertThat(c.stack()).isZero();
    }

    @Test
    void oddChipGoesToFirstWinnerClockwiseFromButton() {
        Player a = player("A", 0, 100);
        Player b = player("B", 3, 100);
        Player folded = player("F", 7, 100);
        a.contribute(5);
        b.contribute(5);
        folded.contribute(5);
        folded.fold();

        List<PotAward> awards = PotManager.settle(
                PotManager.buildPots(List.of(a, b, folded)),
                List.of(a, b, folded), Map.of("A", 42L, "B", 42L), 1, 10);

        assertThat(awards.getFirst().winnings())
                .containsEntry("B", 8)
                .containsEntry("A", 7);
    }

    @Test
    void disconnectedDeadSidePotFallsBackToRemainingContenders() {
        Player deepA = player("A", 0, 1_000);
        Player deepB = player("B", 1, 1_000);
        Player shortC = player("C", 2, 20);
        Player shortD = player("D", 3, 20);
        deepA.contribute(100);
        deepB.contribute(100);
        shortC.contribute(20);
        shortD.contribute(20);
        deepA.fold();
        deepB.fold();

        List<Pot> pots = PotManager.buildPots(List.of(deepA, deepB, shortC, shortD));

        assertThat(pots).containsExactly(
                new Pot(80, List.of("C", "D")),
                new Pot(160, List.of("C", "D"))
        );
        PotManager.settle(
                pots,
                List.of(deepA, deepB, shortC, shortD),
                Map.of("C", 30L, "D", 20L),
                0,
                10
        );
        assertThat(shortC.stack()).isEqualTo(240);
        assertThat(deepA.stack() + deepB.stack() + shortC.stack() + shortD.stack()).isEqualTo(2_040);
    }

    private static Player player(String id, int seat, int stack) {
        return new Player(id, id, seat, stack);
    }
}
