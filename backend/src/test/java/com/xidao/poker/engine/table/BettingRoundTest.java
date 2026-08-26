package com.xidao.poker.engine.table;

import com.xidao.poker.engine.action.ActionErrorCode;
import com.xidao.poker.engine.action.ActionType;
import com.xidao.poker.engine.action.IllegalActionException;
import com.xidao.poker.engine.action.PlayerAction;
import com.xidao.poker.engine.player.Player;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BettingRoundTest {
    @Test
    void allPlayersCanCheckToCompleteAStreet() {
        Player a = player("A", 0, 1_000);
        Player b = player("B", 1, 1_000);
        BettingRound round = new BettingRound(List.of(a, b), 0, 0, 20, 10);

        assertThat(round.act(PlayerAction.check("A")).roundComplete()).isFalse();
        BettingActionResult result = round.act(PlayerAction.check("B"));

        assertThat(result.roundComplete()).isTrue();
        assertThat(result.nextActorSeat()).isNull();
    }

    @Test
    void callPaysExactlyDifferenceAndNeverForcesOneChip() {
        Player a = player("A", 0, 1_000);
        Player b = player("B", 1, 1_000);
        a.contribute(20);
        BettingRound round = new BettingRound(List.of(a, b), 1, 20, 20, 10);

        BettingActionResult call = round.act(PlayerAction.call("B"));

        assertThat(call.paid()).isEqualTo(20);
        assertThat(b.streetBet()).isEqualTo(20);
        assertThat(call.roundComplete()).isFalse();
        assertThat(round.actorSeat()).isEqualTo(0);
        assertThat(round.legalActions("A")).contains(ActionType.CHECK).doesNotContain(ActionType.CALL);
    }

    @Test
    void fullRaiseUpdatesMinimumAndMakesOthersActAgain() {
        Player a = player("A", 0, 1_000);
        Player b = player("B", 1, 1_000);
        Player c = player("C", 2, 1_000);
        a.contribute(20);
        b.contribute(20);
        c.contribute(20);
        BettingRound round = new BettingRound(List.of(a, b, c), 0, 20, 20, 10);
        round.act(PlayerAction.check("A"));
        round.act(PlayerAction.raiseTo("B", 60));

        assertThat(round.currentBet()).isEqualTo(60);
        assertThat(round.minRaise()).isEqualTo(40);
        assertThat(a.hasActed()).isFalse();
        assertThat(b.hasActed()).isTrue();
        assertThat(round.actorSeat()).isEqualTo(2);
    }

    @Test
    void shortAllInDoesNotReopenRaiseForPlayerWhoAlreadyActed() {
        Player a = player("A", 0, 1_000);
        Player b = player("B", 1, 130);
        Player c = player("C", 2, 1_000);
        BettingRound round = new BettingRound(List.of(a, b, c), 0, 0, 100, 10);
        round.act(PlayerAction.bet("A", 100));
        BettingActionResult shortRaise = round.act(PlayerAction.allIn("B"));
        round.act(PlayerAction.call("C"));

        assertThat(shortRaise.fullRaise()).isFalse();
        assertThat(round.currentBet()).isEqualTo(130);
        assertThat(round.minRaise()).isEqualTo(100);
        assertThat(round.actorSeat()).isEqualTo(0);
        assertThat(round.legalActions("A"))
                .contains(ActionType.CALL, ActionType.FOLD)
                .doesNotContain(ActionType.RAISE, ActionType.ALL_IN);
    }

    @Test
    void shortAllInMustUseAllInActionAndCannotMasqueradeAsRaise() {
        Player a = player("A", 0, 130);
        Player b = player("B", 1, 1_000);
        BettingRound round = new BettingRound(List.of(a, b), 0, 100, 100, 10);

        assertThatThrownBy(() -> round.act(PlayerAction.raiseTo("A", 130)))
                .isInstanceOfSatisfying(IllegalActionException.class,
                        error -> assertThat(error.code()).isEqualTo(ActionErrorCode.INVALID_AMOUNT));

        BettingActionResult result = round.act(PlayerAction.allIn("A"));
        assertThat(result.fullRaise()).isFalse();
        assertThat(round.currentBet()).isEqualTo(130);
        assertThat(round.minRaise()).isEqualTo(100);
    }

    @Test
    void cumulativeShortAllInsReopenRaiseAfterReachingAFullRaise() {
        Player a = player("A", 0, 1_000);
        Player b = player("B", 1, 130);
        Player c = player("C", 2, 200);
        Player d = player("D", 3, 1_000);
        BettingRound round = new BettingRound(List.of(a, b, c, d), 0, 0, 100, 10);

        round.act(PlayerAction.bet("A", 100));
        round.act(PlayerAction.allIn("B"));
        round.act(PlayerAction.allIn("C"));
        round.act(PlayerAction.call("D"));

        assertThat(round.actorSeat()).isEqualTo(0);
        assertThat(round.currentBet()).isEqualTo(200);
        assertThat(round.legalActions("A")).contains(ActionType.RAISE, ActionType.ALL_IN);

        BettingActionResult raise = round.act(PlayerAction.raiseTo("A", 300));
        assertThat(raise.fullRaise()).isTrue();
        assertThat(round.minRaise()).isEqualTo(100);
    }

    @Test
    void legalActionsDoNotAdvertiseAnInvalidShortBetOrRaise() {
        Player shortBet = player("A", 0, 10);
        Player opponent = player("B", 1, 1_000);
        BettingRound unopened = new BettingRound(List.of(shortBet, opponent), 0, 0, 20, 10);

        assertThat(unopened.legalActions("A"))
                .contains(ActionType.CHECK, ActionType.ALL_IN)
                .doesNotContain(ActionType.BET);

        Player shortRaise = player("C", 2, 130);
        Player bettor = player("D", 3, 1_000);
        BettingRound opened = new BettingRound(List.of(shortRaise, bettor), 2, 100, 100, 10);

        assertThat(opened.legalActions("C"))
                .contains(ActionType.FOLD, ActionType.CALL, ActionType.ALL_IN)
                .doesNotContain(ActionType.RAISE);
    }

    @Test
    void invalidActionHasStableCodeAndDoesNotMutateState() {
        Player a = player("A", 0, 1_000);
        Player b = player("B", 1, 1_000);
        BettingRound round = new BettingRound(List.of(a, b), 0, 20, 20, 10);

        assertThatThrownBy(() -> round.act(PlayerAction.check("A")))
                .isInstanceOfSatisfying(IllegalActionException.class,
                        error -> assertThat(error.code()).isEqualTo(ActionErrorCode.INVALID_ACTION));
        assertThat(a.stack()).isEqualTo(1_000);
        assertThat(a.streetBet()).isZero();
        assertThat(round.actorSeat()).isEqualTo(0);
    }

    @Test
    void disconnectedAndAllInPlayersNeverEnterRotation() {
        Player a = player("A", 0, 1_000);
        Player disconnected = player("D", 1, 1_000);
        Player allIn = player("I", 2, 20);
        disconnected.disconnect();
        allIn.contribute(20);
        BettingRound round = new BettingRound(List.of(a, disconnected, allIn), 0, 20, 20, 10);

        assertThat(round.actorSeat()).isEqualTo(0);
        assertThat(round.legalActions("D")).isEmpty();
        assertThat(round.legalActions("I")).isEmpty();
    }

    @Test
    void currentActorDisconnectingCannotLeaveAGhostTurn() {
        Player a = player("A", 0, 1_000);
        Player disconnectedActor = player("D", 1, 1_000);
        Player c = player("C", 2, 1_000);
        BettingRound round = new BettingRound(List.of(a, disconnectedActor, c), 1, 20, 20, 10);

        disconnectedActor.disconnectAndForfeitHand();
        boolean complete = round.reconcileAfterEligibilityChange(disconnectedActor.seat());

        assertThat(complete).isFalse();
        assertThat(round.actorSeat()).isEqualTo(2);
        assertThat(round.legalActions("D")).isEmpty();
        assertThat(round.legalActions("C")).contains(ActionType.CALL, ActionType.FOLD);
    }

    @Test
    void spectatorWithChipsIsNeverSelectedAsActor() {
        Player a = player("A", 0, 1_000);
        Player observer = player("O", 1, 1_000);
        Player c = player("C", 2, 1_000);
        observer.becomeSpectator();
        BettingRound round = new BettingRound(List.of(a, observer, c), 1, 0, 20, 10);

        assertThat(round.actorSeat()).isEqualTo(2);
        assertThat(round.legalActions("O")).isEmpty();
    }

    @Test
    void onePlayerWhoCanActAgainstAllInsDoesNotNeedMeaninglessCheck() {
        Player active = player("A", 0, 1_000);
        Player allIn = player("I", 1, 20);
        active.contribute(20);
        allIn.contribute(20);

        BettingRound round = new BettingRound(List.of(active, allIn), 0, 20, 20, 10);

        assertThat(round.isComplete()).isTrue();
        assertThat(round.actorSeat()).isNull();
    }

    @Test
    void actionIntentRejectsAmountsOnNonMonetaryActions() {
        assertThatThrownBy(() -> new PlayerAction("A", ActionType.CALL, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not accept");
    }

    private static Player player(String id, int seat, int stack) {
        return new Player(id, id, seat, stack);
    }
}
