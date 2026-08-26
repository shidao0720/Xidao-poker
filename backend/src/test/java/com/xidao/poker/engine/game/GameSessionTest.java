package com.xidao.poker.engine.game;

import com.xidao.poker.engine.action.ActionType;
import com.xidao.poker.engine.action.PlayerAction;
import com.xidao.poker.engine.event.GameEvent;
import com.xidao.poker.engine.event.GameEventType;
import com.xidao.poker.engine.player.Player;
import com.xidao.poker.engine.player.PlayerStatus;
import com.xidao.poker.engine.snapshot.GameSnapshot;
import com.xidao.poker.engine.snapshot.PlayerSnapshot;
import org.junit.jupiter.api.Test;

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
        session.startGame("A");

        assertThat(session.phase()).isEqualTo(GamePhase.PREFLOP);
        assertThat(session.currentHand()).isNotNull();
        assertThat(session.eventLog()).extracting(GameEvent::type)
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
        session.disconnect("A");
        assertThat(session.snapshot("O").currentActorSeat()).isEqualTo(player(session, "B").seat());

        session.disconnect("B");

        GameSnapshot settled = session.snapshot("O");
        assertThat(settled.phase()).isEqualTo(GamePhase.ROUND_END);
        assertThat(settled.currentActorSeat()).isNull();
        assertThat(observer.status()).isEqualTo(PlayerStatus.SPECTATOR);
        assertThat(session.eventLog())
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
    void ownerDisconnectTransfersOwnershipToNextConnectedPlayer() {
        GameSession session = startedThreePlayerSession();

        List<GameEvent> events = session.disconnect("A");

        assertThat(session.ownerId()).isEqualTo("B");
        assertThat(events).extracting(GameEvent::type).contains(GameEventType.OWNER_CHANGED);
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
        GameSession session = startedThreePlayerSession();

        List<Long> sequences = session.eventLog().stream().map(GameEvent::sequence).toList();

        assertThat(sequences).isSorted().doesNotHaveDuplicates();
        assertThat(sequences.getFirst()).isEqualTo(1L);
        assertThat(session.snapshot("A").lastSequence()).isEqualTo(sequences.getLast());
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
