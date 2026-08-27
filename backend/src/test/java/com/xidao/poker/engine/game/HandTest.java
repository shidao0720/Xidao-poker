package com.xidao.poker.engine.game;

import com.xidao.poker.engine.action.ActionType;
import com.xidao.poker.engine.action.PlayerAction;
import com.xidao.poker.engine.event.GameEventType;
import com.xidao.poker.engine.player.Player;
import com.xidao.poker.engine.player.PlayerStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HandTest {
    private static final GameConfig CONFIG = new GameConfig(10, 20, 1_000, 10);

    @Test
    void startsThreeHandedWithCorrectBlindsDealAndActor() {
        Player button = player("A", 0, 1_000);
        Player smallBlind = player("B", 1, 1_000);
        Player bigBlind = player("C", 2, 1_000);
        Hand hand = new Hand(1, CONFIG, List.of(button, smallBlind, bigBlind), 0, 42L);

        List<com.xidao.poker.engine.event.GameEvent> events = hand.start();

        assertThat(hand.phase()).isEqualTo(GamePhase.PREFLOP);
        assertThat(hand.smallBlindSeat()).isEqualTo(1);
        assertThat(hand.bigBlindSeat()).isEqualTo(2);
        assertThat(hand.currentActorSeat()).isEqualTo(0);
        assertThat(hand.currentBet()).isEqualTo(20);
        assertThat(button.holeCards()).hasSize(2);
        assertThat(smallBlind.holeCards()).hasSize(2);
        assertThat(bigBlind.holeCards()).hasSize(2);
        assertThat(smallBlind.stack()).isEqualTo(990);
        assertThat(bigBlind.stack()).isEqualTo(980);
        assertThat(events).extracting(e -> e.type())
                .contains(GameEventType.HAND_STARTED, GameEventType.BLINDS_POSTED, GameEventType.TURN_CHANGED);
    }

    @Test
    void headsUpButtonPostsSmallBlindAndActsFirstPreflop() {
        Player button = player("A", 0, 1_000);
        Player opponent = player("B", 5, 1_000);
        Hand hand = new Hand(1, CONFIG, List.of(button, opponent), 0, 7L);

        hand.start();

        assertThat(hand.smallBlindSeat()).isEqualTo(0);
        assertThat(hand.bigBlindSeat()).isEqualTo(5);
        assertThat(hand.currentActorSeat()).isEqualTo(0);
    }

    @Test
    void playerActionEventCarriesProjectionStatusForIncrementalClients() {
        Player actor = player("A", 0, 1_000);
        Player smallBlind = player("B", 1, 1_000);
        Player bigBlind = player("C", 2, 1_000);
        Hand hand = new Hand(1, CONFIG, List.of(actor, smallBlind, bigBlind), 0, 9L);
        hand.start();

        var actionEvent = hand.handle(PlayerAction.fold(actor.id())).stream()
                .filter(event -> event.type() == GameEventType.PLAYER_ACTION)
                .findFirst()
                .orElseThrow();

        assertThat(actionEvent.data())
                .containsEntry("status", PlayerStatus.FOLDED.name())
                .containsEntry("canAct", false);
    }

    @Test
    void shortBigBlindStillUsesFullBigBlindAsPreflopBringIn() {
        Player button = player("A", 0, 100);
        Player shortBigBlind = player("B", 1, 5);
        Hand hand = new Hand(1, CONFIG, List.of(button, shortBigBlind), 0, 8L);

        hand.start();

        assertThat(shortBigBlind.status()).isEqualTo(PlayerStatus.ALL_IN);
        assertThat(hand.currentBet()).isEqualTo(CONFIG.bigBlind());
        assertThat(hand.currentActorSeat()).isEqualTo(button.seat());
        assertThat(hand.legalActions(button.id())).contains(ActionType.CALL, ActionType.FOLD);
    }

    @Test
    void checkAndCallFlowRunsAllStreetsAndSettles() {
        Player a = player("A", 0, 1_000);
        Player b = player("B", 1, 1_000);
        Player c = player("C", 2, 1_000);
        Hand hand = new Hand(1, CONFIG, List.of(a, b, c), 0, 99L);
        List<com.xidao.poker.engine.event.GameEvent> events = new ArrayList<>(hand.start());

        events.addAll(playPassivelyUntilSettled(hand));

        assertThat(hand.phase()).isEqualTo(GamePhase.ROUND_END);
        assertThat(hand.communityCards()).hasSize(5);
        assertThat(hand.awards()).isNotEmpty();
        assertThat(a.stack() + b.stack() + c.stack()).isEqualTo(3_000);
        assertThat(hand.currentActorSeat()).isNull();
        assertThat(events).extracting(e -> e.type())
                .contains(GameEventType.SHOWDOWN, GameEventType.SETTLEMENT, GameEventType.HAND_ENDED);
    }

    @Test
    void allInPlayersCauseBoardAndSettlementToAdvanceWithoutGhostActions() {
        Player a = player("A", 0, 20);
        Player b = player("B", 1, 20);
        Hand hand = new Hand(1, CONFIG, List.of(a, b), 0, 123L);
        hand.start();

        assertThat(hand.currentActorSeat()).isEqualTo(0);
        hand.handle(PlayerAction.allIn("A"));

        assertThat(hand.phase()).isEqualTo(GamePhase.ROUND_END);
        assertThat(hand.communityCards()).hasSize(5);
        assertThat(hand.currentActorSeat()).isNull();
        assertThat(a.stack() + b.stack()).isEqualTo(40);
    }

    @Test
    void disconnectingCurrentActorSkipsThemAndSecondDisconnectCannotFreezeHand() {
        Player actor = player("A", 0, 1_000);
        Player next = player("B", 1, 1_000);
        Player bigBlind = player("C", 2, 1_000);
        Hand hand = new Hand(1, CONFIG, List.of(actor, next, bigBlind), 0, 321L);
        hand.start();

        hand.disconnect("A");
        assertThat(actor.status()).isEqualTo(PlayerStatus.DISCONNECTED);
        assertThat(actor.isFolded()).isTrue();
        assertThat(hand.currentActorSeat()).isEqualTo(1);

        hand.disconnect("B");

        assertThat(hand.phase()).isEqualTo(GamePhase.ROUND_END);
        assertThat(hand.currentActorSeat()).isNull();
        assertThat(bigBlind.stack()).isEqualTo(1_010);
    }

    @Test
    void disconnectingAnEarlierAggressorCannotCreateAnUnsettleablePot() {
        Player aggressor = player("A", 0, 1_000);
        Player smallBlind = player("B", 1, 1_000);
        Player bigBlind = player("C", 2, 1_000);
        Hand hand = new Hand(1, CONFIG, List.of(aggressor, smallBlind, bigBlind), 0, 654L);
        hand.start();

        hand.handle(PlayerAction.raiseTo("A", 200));
        hand.disconnect("A");
        hand.handle(PlayerAction.fold("B"));

        assertThat(hand.phase()).isEqualTo(GamePhase.ROUND_END);
        assertThat(hand.currentActorSeat()).isNull();
        assertThat(aggressor.stack() + smallBlind.stack() + bigBlind.stack()).isEqualTo(3_000);
        assertThat(bigBlind.stack()).isEqualTo(1_210);
    }

    @Test
    void reconnectDoesNotReturnForfeitedPlayerToCurrentHand() {
        Player actor = player("A", 0, 1_000);
        Player b = player("B", 1, 1_000);
        Player c = player("C", 2, 1_000);
        Hand hand = new Hand(1, CONFIG, List.of(actor, b, c), 0, 456L);
        hand.start();

        hand.disconnect("A");
        hand.reconnect("A");

        assertThat(actor.status()).isEqualTo(PlayerStatus.FOLDED);
        assertThat(actor.canAct()).isFalse();
        assertThat(hand.legalActions("A")).isEmpty();
        assertThat(hand.currentActorSeat()).isEqualTo(1);
    }

    @Test
    void spectatorCannotEnterHandEvenWithChips() {
        Player a = player("A", 0, 1_000);
        Player observer = player("O", 1, 1_000);
        observer.becomeSpectator();

        assertThatThrownBy(() -> new Hand(1, CONFIG, List.of(a, observer), 0, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("spectators");
    }

    @Test
    void tenPlayerHandCompletesAndConservesAllChips() {
        List<Player> players = java.util.stream.IntStream.range(0, 10)
                .mapToObj(seat -> player("P" + seat, seat, 1_000))
                .toList();
        Hand hand = new Hand(1, CONFIG, players, 0, 777L);
        hand.start();

        playPassivelyUntilSettled(hand);

        assertThat(hand.phase()).isEqualTo(GamePhase.ROUND_END);
        assertThat(hand.currentActorSeat()).isNull();
        assertThat(players).extracting(Player::stack).satisfies(stacks ->
                assertThat(stacks.stream().mapToInt(Integer::intValue).sum()).isEqualTo(10_000));
    }

    @Test
    void hiddenCardsAreVisibleOnlyToOwnerUntilShowdown() {
        Player a = player("A", 0, 1_000);
        Player b = player("B", 1, 1_000);
        Hand hand = new Hand(1, CONFIG, List.of(a, b), 0, 88L);
        hand.start();

        assertThat(hand.visibleHoleCards("A", "A")).hasSize(2);
        assertThat(hand.visibleHoleCards("A", "B")).isEmpty();
        assertThat(hand.visibleHoleCards("spectator", "A")).isEmpty();

        playPassivelyUntilSettled(hand);
        assertThat(hand.visibleHoleCards("spectator", "A")).hasSize(2);
        assertThat(hand.visibleHoleCards("spectator", "B")).hasSize(2);
    }

    private static List<com.xidao.poker.engine.event.GameEvent> playPassivelyUntilSettled(Hand hand) {
        List<com.xidao.poker.engine.event.GameEvent> events = new ArrayList<>();
        int guard = 100;
        while (hand.phase() != GamePhase.ROUND_END && guard-- > 0) {
            Integer actorSeat = hand.currentActorSeat();
            assertThat(actorSeat).as("betting phase must never have a ghost actor").isNotNull();
            Player actor = hand.participants().stream()
                    .filter(p -> p.seat() == actorSeat)
                    .findFirst().orElseThrow();
            Set<ActionType> legal = hand.legalActions(actor.id());
            if (legal.contains(ActionType.CHECK)) {
                events.addAll(hand.handle(PlayerAction.check(actor.id())));
            } else if (legal.contains(ActionType.CALL)) {
                events.addAll(hand.handle(PlayerAction.call(actor.id())));
            } else {
                throw new AssertionError("passive action unavailable: " + legal);
            }
        }
        assertThat(guard).as("hand must terminate").isPositive();
        return List.copyOf(events);
    }

    private static Player player(String id, int seat, int stack) {
        return new Player(id, id, seat, stack);
    }
}
