package com.xidao.poker.engine.pot;

import com.xidao.poker.engine.player.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void fourDifferentAllInsBuildEveryLayerAndRefundUnmatchedTopLayer() {
        Player a = player("A", 0, 100);
        Player b = player("B", 1, 300);
        Player c = player("C", 2, 600);
        Player d = player("D", 3, 1_000);
        a.contribute(100);
        b.contribute(300);
        c.contribute(600);
        d.contribute(1_000);

        List<Pot> pots = PotManager.buildPots(List.of(a, b, c, d));

        assertThat(pots).containsExactly(
                new Pot(400, List.of("A", "B", "C", "D")),
                new Pot(600, List.of("B", "C", "D")),
                new Pot(600, List.of("C", "D")),
                new Pot(400, List.of("D"))
        );

        List<PotAward> awards = PotManager.settle(
                pots,
                List.of(a, b, c, d),
                Map.of("A", 40L, "B", 30L, "C", 20L, "D", 10L),
                3,
                10
        );
        assertThat(awards).extracting(PotAward::winnings).containsExactly(
                Map.of("A", 400),
                Map.of("B", 600),
                Map.of("C", 600),
                Map.of("D", 400)
        );
        assertThat(List.of(a, b, c, d)).extracting(Player::stack).containsExactly(400, 600, 600, 400);
    }

    @Test
    void mainPotTieAndSidePotWinnerAreSettledIndependently() {
        Player a = player("A", 0, 100);
        Player b = player("B", 1, 300);
        Player c = player("C", 2, 300);
        a.contribute(100);
        b.contribute(300);
        c.contribute(300);

        List<PotAward> awards = PotManager.settle(
                PotManager.buildPots(List.of(a, b, c)),
                List.of(a, b, c),
                Map.of("A", 50L, "B", 50L, "C", 40L),
                2,
                10
        );

        assertThat(awards.get(0).winnings()).containsAllEntriesOf(Map.of("A", 150, "B", 150)).hasSize(2);
        assertThat(awards.get(1).winnings()).containsExactlyEntriesOf(Map.of("B", 400));
        assertThat(a.stack()).isEqualTo(150);
        assertThat(b.stack()).isEqualTo(550);
        assertThat(c.stack()).isZero();
    }

    @Test
    void disconnectedAllInPlayerRemainsEligibleAtShowdown() {
        Player disconnected = player("A", 0, 100);
        Player b = player("B", 1, 100);
        Player c = player("C", 2, 100);
        disconnected.contribute(100);
        b.contribute(100);
        c.contribute(100);
        disconnected.disconnect();

        List<Pot> pots = PotManager.buildPots(List.of(disconnected, b, c));
        assertThat(pots).containsExactly(new Pot(300, List.of("A", "B", "C")));

        PotManager.settle(
                pots,
                List.of(disconnected, b, c),
                Map.of("A", 30L, "B", 20L, "C", 10L),
                0,
                10
        );
        assertThat(disconnected.stack()).isEqualTo(300);
    }

    @Test
    void invalidLaterPotCannotPartiallyMutateEarlierWinnings() {
        Player a = player("A", 0, 200);
        Player b = player("B", 1, 200);
        a.contribute(100);
        b.contribute(100);
        List<Pot> invalid = List.of(
                new Pot(100, List.of("A")),
                new Pot(100, List.of("B"))
        );

        assertThatThrownBy(() -> PotManager.settle(
                invalid,
                List.of(a, b),
                Map.of("A", 10L),
                0,
                10
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing hand key");
        assertThat(a.stack()).isEqualTo(100);
        assertThat(b.stack()).isEqualTo(100);
    }

    @Test
    void externallySuppliedPotCannotAwardFoldedPlayer() {
        Player folded = player("A", 0, 200);
        Player active = player("B", 1, 200);
        folded.contribute(100);
        active.contribute(100);
        folded.fold();

        assertThatThrownBy(() -> PotManager.settle(
                List.of(new Pot(200, List.of("A"))),
                List.of(folded, active),
                Map.of("A", 99L),
                0,
                10
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ineligible");
        assertThat(folded.stack()).isEqualTo(100);
        assertThat(active.stack()).isEqualTo(100);
    }

    @Test
    void deterministicRandomAllInsAlwaysConserveEveryChip() {
        Random random = new Random(20260826L);
        for (int scenario = 0; scenario < 200; scenario++) {
            int playerCount = 2 + random.nextInt(9);
            List<Player> players = new ArrayList<>();
            Map<String, Long> handKeys = new java.util.LinkedHashMap<>();
            int initialTotal = 0;
            for (int seat = 0; seat < playerCount; seat++) {
                int chips = 1 + random.nextInt(500);
                Player player = player("P" + seat, seat, chips);
                player.contribute(chips);
                players.add(player);
                handKeys.put(player.id(), (long) random.nextInt(8));
                initialTotal = Math.addExact(initialTotal, chips);
            }

            List<Pot> pots = PotManager.buildPots(players);
            assertThat(pots.stream().mapToInt(Pot::amount).sum()).isEqualTo(initialTotal);

            List<PotAward> awards = PotManager.settle(
                    pots,
                    players,
                    handKeys,
                    random.nextInt(playerCount),
                    10
            );
            assertThat(awards.stream().mapToInt(PotAward::potAmount).sum()).isEqualTo(initialTotal);
            assertThat(players.stream().mapToInt(Player::stack).sum()).isEqualTo(initialTotal);
            assertThat(awards).allSatisfy(award ->
                    assertThat(award.winnings().values().stream().mapToInt(Integer::intValue).sum())
                            .isEqualTo(award.potAmount()));
        }
    }

    private static Player player(String id, int seat, int stack) {
        Player player = new Player(id, id, seat, stack);
        player.beginHand();
        return player;
    }
}
