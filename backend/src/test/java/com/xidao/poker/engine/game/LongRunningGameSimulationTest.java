package com.xidao.poker.engine.game;

import com.xidao.poker.engine.action.ActionType;
import com.xidao.poker.engine.action.PlayerAction;
import com.xidao.poker.engine.player.ConnectionStatus;
import com.xidao.poker.engine.player.HandStatus;
import com.xidao.poker.engine.player.Player;
import com.xidao.poker.engine.snapshot.ActionOptions;
import com.xidao.poker.engine.snapshot.GameSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class LongRunningGameSimulationTest {
    private static final GameConfig CONFIG = new GameConfig(5, 10, 1_000, 10);
    private static final int PLAYER_COUNT = 10;
    private static final int EXPECTED_CHIPS = PLAYER_COUNT * CONFIG.buyIn();

    @Test
    @Timeout(30)
    void oneThousandHandsPreserveChipsAndNeverExposeAGhostActor() {
        Random decisions = new Random(0xFA7E_2026L);
        int completedHands = 0;
        int tournament = 0;

        while (completedHands < 1_000) {
            GameSession session = newSession(++tournament);
            while (completedHands < 1_000 && playersWithChips(session) >= 2) {
                session.startGame(session.ownerId());
                assertInvariants(session);

                int actionGuard = 2_000;
                while (session.phase() != GamePhase.ROUND_END && actionGuard-- > 0) {
                    GameSnapshot table = session.snapshot(session.ownerId());
                    Integer actorSeat = table.currentActorSeat();
                    assertThat(actorSeat).as("active street must expose an actor").isNotNull();
                    Player actor = session.players().stream()
                            .filter(player -> player.seat() == actorSeat)
                            .findFirst()
                            .orElseThrow();
                    assertThat(actor.canAct()).as("currentActor.canAct() must hold").isTrue();

                    if (decisions.nextInt(40) == 0) {
                        session.disconnect(actor.id());
                        assertThat(actor.connectionStatus()).isEqualTo(ConnectionStatus.DISCONNECTED);
                        assertThat(actor.handStatus()).isEqualTo(
                                session.handInProgress() ? HandStatus.FOLDED : HandStatus.NOT_IN_HAND);
                        session.reconnect(actor.id());
                    } else {
                        GameSnapshot actorView = session.snapshot(actor.id());
                        PlayerAction action = randomLegalAction(actor.id(), actorView.actionOptions(), decisions);
                        session.handle(actorView.handId(), actorView.turnId(), action);

                        if (action.type() == ActionType.ALL_IN
                                && session.handInProgress()
                                && actor.handStatus() == HandStatus.ALL_IN) {
                            session.disconnect(actor.id());
                            assertThat(actor.connectionStatus()).isEqualTo(ConnectionStatus.DISCONNECTED);
                            assertThat(actor.handStatus()).isEqualTo(HandStatus.ALL_IN);
                            assertThat(actor.isInHand()).isTrue();
                            session.reconnect(actor.id());
                        }
                    }
                    assertInvariants(session);
                }
                assertThat(actionGuard).as("simulated hand must terminate").isPositive();
                assertThat(session.phase()).isEqualTo(GamePhase.ROUND_END);
                assertInvariants(session);
                completedHands++;
            }
        }

        assertThat(completedHands).isEqualTo(1_000);
    }

    private static GameSession newSession(int tournament) {
        GameSession session = new GameSession("simulation-" + tournament, CONFIG, 10_000L + tournament);
        for (int seat = 0; seat < PLAYER_COUNT; seat++) {
            String id = "P" + seat;
            session.addPlayer(id, id);
            session.setReady(id, true);
        }
        return session;
    }

    private static PlayerAction randomLegalAction(String playerId, ActionOptions options, Random random) {
        List<ActionType> legal = new ArrayList<>(options.legalActions());
        legal.sort(Comparator.comparingInt(Enum::ordinal));
        assertThat(legal).as("server must provide at least one legal action").isNotEmpty();
        ActionType selected = legal.get(random.nextInt(legal.size()));
        return switch (selected) {
            case FOLD -> PlayerAction.fold(playerId);
            case CHECK -> PlayerAction.check(playerId);
            case CALL -> PlayerAction.call(playerId);
            case ALL_IN -> PlayerAction.allIn(playerId);
            case BET -> PlayerAction.bet(playerId, randomAmount(
                    options.minimumBetTo(), options.maximumTo(), random));
            case RAISE -> PlayerAction.raiseTo(playerId, randomAmount(
                    options.minimumRaiseTo(), options.maximumTo(), random));
        };
    }

    private static int randomAmount(Integer minimum, int maximum, Random random) {
        if (minimum == null || minimum <= 0 || minimum > maximum) {
            throw new AssertionError("server exposed an invalid bet/raise range");
        }
        long width = (long) maximum - minimum + 1;
        return Math.toIntExact(minimum + random.nextLong(width));
    }

    private static void assertInvariants(GameSession session) {
        List<Player> players = session.players();
        assertThat(players).allSatisfy(player -> assertThat(player.stack()).isNotNegative());

        long accounted = players.stream().mapToLong(Player::stack).sum();
        if (session.phase() != GamePhase.ROUND_END) {
            accounted += session.currentHand().potAmount();
        }
        assertThat(accounted).as("all play-money chips must be conserved").isEqualTo(EXPECTED_CHIPS);

        Integer actorSeat = session.snapshot(session.ownerId()).currentActorSeat();
        if (actorSeat != null) {
            Player actor = players.stream().filter(player -> player.seat() == actorSeat).findFirst().orElseThrow();
            assertThat(actor.canAct()).as("currentActor.canAct() must hold").isTrue();
            assertThat(actor.connectionStatus()).isEqualTo(ConnectionStatus.CONNECTED);
            assertThat(actor.handStatus()).isEqualTo(HandStatus.ACTIVE);
        }
    }

    private static long playersWithChips(GameSession session) {
        return session.players().stream().filter(player -> player.stack() > 0).count();
    }
}
