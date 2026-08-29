package com.xidao.poker.engine.game;

import com.xidao.poker.engine.action.ActionErrorCode;
import com.xidao.poker.engine.action.ActionType;
import com.xidao.poker.engine.action.IllegalActionException;
import com.xidao.poker.engine.action.PlayerAction;
import com.xidao.poker.engine.event.GameEvent;
import com.xidao.poker.engine.event.GameEventType;
import com.xidao.poker.engine.player.Player;
import com.xidao.poker.engine.player.PlayerStatus;
import com.xidao.poker.engine.snapshot.GameSnapshot;
import com.xidao.poker.engine.snapshot.PlayerSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameSessionTest {
    private static final GameConfig CONFIG = new GameConfig(10, 20, 1_000, 10);

    @Test
    void requiresEveryoneToBeReadyAndStartsFirstHand() {
        GameSession session = session(CONFIG, 10L, "A", "B");
        session.setReady("A", true);

        assertThat(session.phase()).isEqualTo(GamePhase.WAITING);
        assertThatThrownBy(() -> session.startGame("A"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ready");

        session.setReady("B", true);
        assertThat(session.phase()).isEqualTo(GamePhase.READY);
        List<GameEvent> events = session.startGame("A");

        assertThat(session.phase()).isEqualTo(GamePhase.PREFLOP);
        assertThat(session.currentHand()).isNotNull();
        assertThat(events).extracting(GameEvent::type)
                .contains(GameEventType.GAME_STARTED, GameEventType.HAND_STARTED);
    }

    @Test
    void midHandJoinerIsSpectatorAndCannotSeeOrReceiveATurn() {
        GameSession session = startedThreePlayerSession();
        session.addPlayer("O", "Observer");

        Player observer = player(session, "O");
        GameSnapshot snapshot = session.snapshot("O");

        assertThat(observer.status()).isEqualTo(PlayerStatus.SPECTATOR);
        assertThat(snapshot.legalActions()).isEmpty();
        assertThat(snapshot.players())
                .filteredOn(p -> !p.id().equals("O"))
                .allSatisfy(p -> assertThat(p.holeCards()).isEmpty());
        assertThat(snapshot.currentActorSeat()).isNotEqualTo(observer.seat());
        assertThat(session.currentHand().containsPlayer("O")).isFalse();
    }

    @Test
    void disconnectedActorsAndSpectatorsCannotCreateGhostTurn() {
        GameSession session = startedThreePlayerSession();
        session.addPlayer("O", "Observer");
        Player observer = player(session, "O");

        assertThat(session.snapshot("O").currentActorSeat()).isEqualTo(player(session, "A").seat());
        List<GameEvent> events = new ArrayList<>(session.disconnect("A"));
        assertThat(session.snapshot("O").currentActorSeat()).isEqualTo(player(session, "B").seat());

        events.addAll(session.disconnect("B"));

        GameSnapshot settled = session.snapshot("O");
        assertThat(settled.phase()).isEqualTo(GamePhase.ROUND_END);
        assertThat(settled.currentActorSeat()).isNull();
        assertThat(observer.status()).isEqualTo(PlayerStatus.SPECTATOR);
        assertThat(events)
                .filteredOn(e -> e.type() == GameEventType.TURN_CHANGED)
                .allSatisfy(event -> assertThat(event.playerId()).isNotEqualTo("O"));
    }

    @Test
    void reconnectReturnsSnapshotButNotActionRightsForForfeitedHand() {
        GameSession session = startedThreePlayerSession();
        session.disconnect("A");
        session.reconnect("A");

        GameSnapshot snapshot = session.snapshot("A");

        assertThat(player(session, "A").status()).isEqualTo(PlayerStatus.FOLDED);
        assertThat(snapshot.legalActions()).isEmpty();
        assertThat(snapshot.players()).filteredOn(p -> p.id().equals("A")).singleElement()
                .satisfies(p -> assertThat(p.holeCards()).hasSize(2));
        assertThat(snapshot.currentActorSeat()).isEqualTo(player(session, "B").seat());
    }

    @Test
    void reconnectingAfterMissingNextHandWaitsAsSpectator() {
        GameSession session = startedThreePlayerSession();
        session.disconnect("A");
        playPassivelyUntilSettled(session);
        session.startGame(session.ownerId());

        assertThat(session.currentHand().containsPlayer("A")).isFalse();
        session.reconnect("A");

        GameSnapshot snapshot = session.snapshot("A");
        assertThat(player(session, "A").status()).isEqualTo(PlayerStatus.SPECTATOR);
        assertThat(snapshot.legalActions()).isEmpty();
        assertThat(snapshot.players()).filteredOn(p -> p.id().equals("A")).singleElement()
                .satisfies(player -> {
                    assertThat(player.inHand()).isFalse();
                    assertThat(player.canAct()).isFalse();
                    assertThat(player.holeCards()).isEmpty();
                });
    }

    @Test
    void staleHandAndTurnCommandsNeverMutateChips() {
        GameSession session = startedThreePlayerSession();
        GameSnapshot actor = session.snapshot("A");

        assertThatThrownBy(() -> session.handle(
                actor.handId() + 1,
                actor.turnId(),
                PlayerAction.call("A")
        )).isInstanceOfSatisfying(IllegalActionException.class,
                error -> assertThat(error.code()).isEqualTo(ActionErrorCode.STALE_HAND));
        assertThat(player(session, "A").stack()).isEqualTo(1_000);

        session.handle(actor.handId(), actor.turnId(), PlayerAction.call("A"));
        assertThat(player(session, "A").stack()).isEqualTo(980);

        assertThatThrownBy(() -> session.handle(
                actor.handId(),
                actor.turnId(),
                PlayerAction.call("A")
        )).isInstanceOfSatisfying(IllegalActionException.class,
                error -> assertThat(error.code()).isEqualTo(ActionErrorCode.STALE_TURN));
        assertThat(player(session, "A").stack()).isEqualTo(980);
    }

    @Test
    void ownerDisconnectTransfersOwnershipToNextConnectedPlayer() {
        GameSession session = startedThreePlayerSession();

        List<GameEvent> events = session.disconnect("A");

        assertThat(session.ownerId()).isEqualTo("B");
        assertThat(events).extracting(GameEvent::type).contains(GameEventType.OWNER_CHANGED);
    }

    @Test
    void firstNewConnectedPlayerReplacesAnOtherwiseStrandedOfflineOwner() {
        GameSession session = session(CONFIG, 9L, "A");
        session.disconnect("A");

        List<GameEvent> events = session.addPlayer("B", "B");

        assertThat(session.ownerId()).isEqualTo("B");
        assertThat(events).extracting(GameEvent::type)
                .containsExactly(GameEventType.PLAYER_JOINED, GameEventType.OWNER_CHANGED);
    }

    @Test
    void nextHandRotatesButtonAndReinitializesHandState() {
        GameSession session = startedThreePlayerSession();
        int firstButton = session.currentHand().buttonSeat();
        playPassivelyUntilSettled(session);
        long firstHandId = session.currentHand().id();

        session.startGame(session.ownerId());

        Hand next = session.currentHand();
        assertThat(next.id()).isEqualTo(firstHandId + 1);
        assertThat(next.buttonSeat()).isEqualTo(Math.floorMod(firstButton + 1, CONFIG.maxPlayers()));
        assertThat(next.communityCards()).isEmpty();
        assertThat(next.potAmount()).isEqualTo(CONFIG.smallBlind() + CONFIG.bigBlind());
        assertThat(next.participants()).allSatisfy(p -> assertThat(p.holeCards()).hasSize(2));
    }

    @Test
    void bustedPlayerIsNotConvertedToSpectatorOrDealtIntoNextHand() {
        GameConfig shortStack = new GameConfig(10, 20, 20, 10);
        GameSession session = session(shortStack, 123L, "A", "B");
        session.setReady("A", true);
        session.setReady("B", true);
        session.startGame("A");
        session.handle(PlayerAction.allIn("A"));

        List<Player> busted = session.players().stream().filter(Player::isBusted).toList();
        assertThat(busted).hasSize(1);
        assertThat(busted.getFirst().status()).isEqualTo(PlayerStatus.BUSTED);

        session.addPlayer("C", "C");
        session.setReady("C", true);
        session.startGame(session.ownerId());

        assertThat(session.currentHand().participants()).extracting(Player::id)
                .doesNotContain(busted.getFirst().id())
                .contains("C");
    }

    @Test
    void eventSequencesAreStrictlyIncreasing() {
        GameSession session = new GameSession("room-1", CONFIG, 42L);
        List<GameEvent> events = new ArrayList<>();
        for (String id : List.of("A", "B", "C")) events.addAll(session.addPlayer(id, id));
        for (String id : List.of("A", "B", "C")) events.addAll(session.setReady(id, true));
        events.addAll(session.startGame("A"));

        List<Long> sequences = events.stream().map(GameEvent::sequence).toList();

        assertThat(sequences).isSorted().doesNotHaveDuplicates();
        assertThat(sequences.getFirst()).isEqualTo(1L);
        assertThat(session.snapshot("A").lastSequence()).isEqualTo(sequences.getLast());
    }

    @Test
    void tenPlayerHandCompletesWithoutGhostActorsAndConservesChips() {
        String[] ids = {"P0", "P1", "P2", "P3", "P4", "P5", "P6", "P7", "P8", "P9"};
        GameSession session = session(CONFIG, 8080L, ids);
        session.players().forEach(player -> session.setReady(player.id(), true));

        session.startGame("P0");

        assertThat(session.currentHand().participants()).hasSize(10)
                .allSatisfy(player -> assertThat(player.holeCards()).hasSize(2));
        playPassivelyUntilSettled(session);
        assertThat(session.phase()).isEqualTo(GamePhase.ROUND_END);
        assertThat(session.players().stream().mapToInt(Player::stack).sum()).isEqualTo(10_000);
        assertThat(session.snapshot("P0").currentActorSeat()).isNull();
    }

    @Test
    void settledSnapshotCarriesAuthoritativeWinnerCategoryAndVisibleCards() {
        GameSession session = startedThreePlayerSession();
        playPassivelyUntilSettled(session);

        GameSnapshot settled = session.snapshot("A");
        Set<String> winnerIds = settled.awards().stream()
                .flatMap(award -> award.winnings().keySet().stream())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(settled.revealedHands())
                .filteredOn(result -> winnerIds.contains(result.playerId()))
                .isNotEmpty()
                .allSatisfy(result -> {
                    assertThat(result.category()).isNotNull();
                    assertThat(result.bestCards()).hasSize(5);
                });
        assertThat(settled.players())
                .filteredOn(player -> winnerIds.contains(player.id()))
                .allSatisfy(player -> assertThat(player.holeCards()).hasSize(2));
    }

    @Test
    void deterministicSeedReproducesTheSamePrivateDeal() {
        GameSession first = session(CONFIG, 4242L, "A", "B", "C");
        GameSession second = session(CONFIG, 4242L, "A", "B", "C");
        first.players().forEach(player -> first.setReady(player.id(), true));
        second.players().forEach(player -> second.setReady(player.id(), true));

        first.startGame("A");
        second.startGame("A");

        for (String playerId : List.of("A", "B", "C")) {
            List<com.xidao.poker.engine.card.Card> firstCards = first.snapshot(playerId).players().stream()
                    .filter(player -> player.id().equals(playerId))
                    .findFirst().orElseThrow().holeCards();
            List<com.xidao.poker.engine.card.Card> secondCards = second.snapshot(playerId).players().stream()
                    .filter(player -> player.id().equals(playerId))
                    .findFirst().orElseThrow().holeCards();
            assertThat(firstCards).containsExactlyElementsOf(secondCards);
        }
    }

    private static GameSession startedThreePlayerSession() {
        GameSession session = session(CONFIG, 42L, "A", "B", "C");
        session.players().forEach(p -> session.setReady(p.id(), true));
        session.startGame("A");
        return session;
    }

    private static GameSession session(GameConfig config, long seed, String... ids) {
        GameSession session = new GameSession("room-1", config, seed);
        for (String id : ids) session.addPlayer(id, id);
        return session;
    }

    private static Player player(GameSession session, String id) {
        return session.players().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
    }

    private static void playPassivelyUntilSettled(GameSession session) {
        int guard = 100;
        while (session.phase() != GamePhase.ROUND_END && guard-- > 0) {
            GameSnapshot anyView = session.snapshot(session.ownerId());
            Integer actorSeat = anyView.currentActorSeat();
            assertThat(actorSeat).as("session must not expose a ghost actor").isNotNull();
            Player actor = session.players().stream().filter(p -> p.seat() == actorSeat).findFirst().orElseThrow();
            Set<ActionType> legal = session.snapshot(actor.id()).legalActions();
            if (legal.contains(ActionType.CHECK)) session.handle(PlayerAction.check(actor.id()));
            else if (legal.contains(ActionType.CALL)) session.handle(PlayerAction.call(actor.id()));
            else throw new AssertionError("no passive action for " + actor.id() + ": " + legal);
        }
        assertThat(guard).as("session hand must terminate").isPositive();
    }
}
