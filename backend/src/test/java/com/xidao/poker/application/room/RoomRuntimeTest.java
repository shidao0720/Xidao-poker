package com.xidao.poker.application.room;

import com.xidao.poker.application.command.PlayerActionCommand;
import com.xidao.poker.application.command.StartGameCommand;
import com.xidao.poker.application.command.TurnTimeoutCommand;
import com.xidao.poker.engine.action.ActionErrorCode;
import com.xidao.poker.engine.action.ActionType;
import com.xidao.poker.engine.action.IllegalActionException;
import com.xidao.poker.engine.event.GameEvent;
import com.xidao.poker.engine.event.GameEventType;
import com.xidao.poker.engine.game.GameConfig;
import com.xidao.poker.engine.game.GamePhase;
import com.xidao.poker.engine.player.ConnectionStatus;
import com.xidao.poker.engine.player.HandStatus;
import com.xidao.poker.engine.player.PlayerStatus;
import com.xidao.poker.engine.player.SeatStatus;
import com.xidao.poker.engine.snapshot.GameSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoomRuntimeTest {
    private static final GameConfig CONFIG = new GameConfig(10, 20, 1_000, 10);

    @Test
    void startCreatesViewerFilteredSnapshotsWithServerCalculatedActionOptions() {
        StartedRoom started = startedRoom();
        GameSnapshot actor = started.startResult().requesterSnapshot();
        GameSnapshot opponent = started.runtime().requestSnapshot("B", "socket-B", 1);

        assertThat(actor.handId()).isPositive();
        assertThat(actor.turnId()).isPositive();
        assertThat(actor.currentActorSeat()).isEqualTo(0);
        assertThat(actor.pot()).isEqualTo(30);
        assertThat(actor.pots()).extracting(pot -> pot.amount()).containsExactly(20, 10);
        assertThat(actor.actionOptions().toCall()).isEqualTo(20);
        assertThat(actor.actionOptions().callAmount()).isEqualTo(20);
        assertThat(actor.actionOptions().minimumRaiseTo()).isEqualTo(40);
        assertThat(actor.legalActions()).contains(ActionType.CALL, ActionType.RAISE, ActionType.FOLD);
        assertThat(actor.players()).filteredOn(player -> player.id().equals("A")).singleElement()
                .satisfies(player -> assertThat(player.holeCards()).hasSize(2));
        assertThat(actor.players()).filteredOn(player -> player.id().equals("B")).singleElement()
                .satisfies(player -> assertThat(player.holeCards()).isEmpty());
        assertThat(opponent.legalActions()).isEmpty();
    }

    @Test
    void duplicateCommandDoesNotChargeTwiceAndExpiredTurnIsRejected() {
        StartedRoom started = startedRoom();
        RoomRuntime runtime = started.runtime();
        GameSnapshot actor = started.startResult().requesterSnapshot();
        PlayerActionCommand command = action("action-1", "A", actor, ActionType.CALL);

        RoomExecutionResult first = runtime.act(command);
        RoomExecutionResult duplicate = runtime.act(command);

        assertThat(first.duplicate()).isFalse();
        assertThat(duplicate.duplicate()).isTrue();
        assertThat(duplicate.events()).isEmpty();
        GameSnapshot after = duplicate.requesterSnapshot();
        assertThat(after.players()).filteredOn(player -> player.id().equals("A")).singleElement()
                .satisfies(player -> assertThat(player.stack()).isEqualTo(980));

        PlayerActionCommand expired = new PlayerActionCommand(
                ROOM_ID,
                "action-2",
                "A",
                "socket-A",
                1,
                actor.handId(),
                actor.turnId(),
                ActionType.CALL,
                0
        );
        assertThatThrownBy(() -> runtime.act(expired))
                .isInstanceOfSatisfying(IllegalActionException.class,
                        error -> assertThat(error.code()).isEqualTo(ActionErrorCode.STALE_TURN));
    }

    @Test
    void replacedConnectionCannotActReplayOrUseDuplicateCommandForPrivateSnapshot() {
        StartedRoom started = startedRoom();
        RoomRuntime runtime = started.runtime();
        GameSnapshot actor = started.startResult().requesterSnapshot();

        PlayerActionCommand forged = new PlayerActionCommand(
                ROOM_ID,
                "forged-new",
                "A",
                "socket-attacker",
                1,
                actor.handId(),
                actor.turnId(),
                ActionType.CALL,
                0
        );
        assertStaleConnection(() -> runtime.act(forged));
        assertStaleConnection(() -> runtime.replayAfter(
                "A", "socket-attacker", 1, actor.lastSequence()));

        PlayerActionCommand accepted = action("accepted-action", "A", actor, ActionType.CALL);
        runtime.act(accepted);
        PlayerActionCommand forgedDuplicate = new PlayerActionCommand(
                ROOM_ID,
                accepted.commandId(),
                "A",
                "socket-attacker",
                1,
                accepted.handId(),
                accepted.turnId(),
                accepted.action(),
                accepted.amount()
        );
        assertStaleConnection(() -> runtime.act(forgedDuplicate));

        GameSnapshot unchanged = runtime.requestSnapshot("A", "socket-A", 1);
        assertThat(unchanged.players()).filteredOn(player -> player.id().equals("A")).singleElement()
                .satisfies(player -> assertThat(player.stack()).isEqualTo(980));
    }

    @Test
    void delayedStartCommandCannotOpenAHandFromAnOlderRoomState() {
        StartedRoom started = startedRoom();

        assertThatThrownBy(() -> started.runtime().startGame(new StartGameCommand(
                ROOM_ID,
                "delayed-start",
                "A",
                "socket-A",
                1,
                0
        ))).isInstanceOfSatisfying(IllegalActionException.class,
                error -> assertThat(error.code()).isEqualTo(ActionErrorCode.STALE_HAND));
        assertThat(started.runtime().summary().phase()).isEqualTo(GamePhase.PREFLOP);
    }

    @Test
    void concurrentCommandsForOneTurnProduceExactlyOneMutation() throws Exception {
        StartedRoom started = startedRoom();
        RoomRuntime runtime = started.runtime();
        GameSnapshot actor = started.startResult().requesterSnapshot();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = executor.submit(() -> raceAction(runtime, action("race-1", "A", actor, ActionType.CALL), ready, go));
            Future<Object> second = executor.submit(() -> raceAction(runtime, action("race-2", "A", actor, ActionType.CALL), ready, go));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            List<Object> results = List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
            assertThat(results).filteredOn(RoomExecutionResult.class::isInstance).hasSize(1);
            assertThat(results).filteredOn(result -> result == ActionErrorCode.STALE_TURN).hasSize(1);

            GameSnapshot snapshot = runtime.requestSnapshot("A", "socket-A", 1);
            assertThat(snapshot.players()).filteredOn(player -> player.id().equals("A")).singleElement()
                    .satisfies(player -> assertThat(player.stack()).isEqualTo(980));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void reconnectAtomicallyReturnsPrivateSnapshotAndOldSocketCannotDisconnectReplacement() {
        StartedRoom started = startedRoom();
        RoomRuntime runtime = started.runtime();
        runtime.drainOutbox();

        runtime.disconnect("disconnect-A", "A", "socket-A", 1);
        RoomExecutionResult reconnect = runtime.reconnect("reconnect-A", "A", 1, "socket-A-2");

        assertThat(reconnect.connectionEpoch()).isEqualTo(2);
        assertThat(reconnect.requesterSnapshot()).isNotNull();
        GameSnapshot snapshot = reconnect.requesterSnapshot();
        assertThat(snapshot.players()).filteredOn(player -> player.id().equals("A")).singleElement()
                .satisfies(player -> {
                    assertThat(player.status()).isEqualTo(PlayerStatus.FOLDED);
                    assertThat(player.holeCards()).hasSize(2);
                    assertThat(player.canAct()).isFalse();
                });
        assertThat(snapshot.legalActions()).isEmpty();
        assertThat(runtime.drainOutbox()).anySatisfy(delivery ->
                assertThat(delivery).isInstanceOfSatisfying(RoomDelivery.Snapshot.class,
                        targeted -> {
                            assertThat(targeted.playerId()).isEqualTo("A");
                            assertThat(targeted.connectionId()).isEqualTo("socket-A-2");
                            assertThat(targeted.connectionEpoch()).isEqualTo(2);
                        }));

        RoomExecutionResult staleDisconnect = runtime.disconnect("old-close", "A", "socket-A", 1);
        assertThat(staleDisconnect.ignored()).isTrue();
        assertThat(staleDisconnect.requesterSnapshot()).isNull();
        assertThat(runtime.connection("A")).contains(
                new MemberConnection("socket-A-2", 2, ConnectionState.CONNECTED)
        );
        assertStaleConnection(() -> runtime.reconnect(
                "late-reconnect-A", "A", 1, "socket-A-stale"));
    }

    @Test
    void expiredDisconnectTaskCannotRemoveAReconnectedPlayer() {
        StartedRoom started = startedRoom();
        RoomRuntime runtime = started.runtime();

        runtime.disconnect("disconnect-B-1", "B", "socket-B", 1);
        RoomExecutionResult reconnect = runtime.reconnect("reconnect-B-1", "B", 1, "socket-B-2");
        RoomExecutionResult staleExpiry = runtime.expireDisconnected("expiry-B-old", "B", 1);

        assertThat(reconnect.connectionEpoch()).isEqualTo(2);
        assertThat(staleExpiry.ignored()).isTrue();
        assertThat(runtime.connection("B")).contains(
                new MemberConnection("socket-B-2", 2, ConnectionState.CONNECTED));

        runtime.disconnect("disconnect-B-2", "B", "socket-B-2", 2);
        RoomExecutionResult currentExpiry = runtime.expireDisconnected("expiry-B-current", "B", 2);
        assertThat(currentExpiry.ignored()).isFalse();
        assertThat(runtime.connection("B")).contains(
                new MemberConnection("socket-B-2", 2, ConnectionState.CLOSED));
    }

    @Test
    void eventReplayCommandCacheAndOutboxRemainBounded() {
        RoomRuntime runtime = runtime(3, 3, CONFIG.maxPlayers());
        runtime.join("join-A", "A", "A", "socket-A");
        runtime.join("join-B", "B", "B", "socket-B");
        runtime.join("join-C", "C", "C", "socket-C");
        runtime.setReady("ready-A", "A", "socket-A", 1, true);
        runtime.setReady("ready-B", "B", "socket-B", 1, true);
        RoomExecutionResult last = runtime.setReady("ready-C", "C", "socket-C", 1, true);

        assertThat(runtime.replaySize()).isEqualTo(3);
        assertThat(runtime.processedCommandCount()).isEqualTo(3);
        assertThat(runtime.pendingDeliveryCount()).isLessThanOrEqualTo(CONFIG.maxPlayers());
        assertThat(runtime.replayAfter("A", "socket-A", 1, 0).snapshotRequired()).isTrue();

        EventReplay latest = runtime.replayAfter(
                "A", "socket-A", 1, last.lastSequence() - 1);
        assertThat(latest.snapshotRequired()).isFalse();
        assertThat(latest.events()).singleElement()
                .satisfies(event -> assertThat(event.sequence()).isEqualTo(last.lastSequence()));
    }

    @Test
    void staleTimeoutIsIgnoredAndCurrentTimeoutUsesCheckOrFoldPolicy() {
        StartedRoom started = startedRoom();
        RoomRuntime runtime = started.runtime();
        GameSnapshot actor = started.startResult().requesterSnapshot();
        TurnTimeoutCommand timeout = new TurnTimeoutCommand(
                ROOM_ID, "timeout-1", "A", actor.handId(), actor.turnId());

        RoomExecutionResult applied = runtime.timeout(timeout);

        assertThat(applied.ignored()).isFalse();
        assertThat(applied.events()).filteredOn(event -> event.type() == GameEventType.PLAYER_ACTION)
                .singleElement()
                .satisfies(event -> assertThat(event.data()).containsEntry("action", "FOLD"));

        RoomExecutionResult stale = runtime.timeout(new TurnTimeoutCommand(
                ROOM_ID, "timeout-2", "A", actor.handId(), actor.turnId()));
        assertThat(stale.ignored()).isTrue();
        assertThat(stale.events()).isEmpty();
    }

    @Test
    void leavingDuringHandDefersRemovalUntilSettlement() {
        StartedRoom started = startedRoom();
        RoomRuntime runtime = started.runtime();

        RoomExecutionResult leave = runtime.leave("leave-A", "A", "socket-A", 1);
        assertThat(runtime.summary().playerCount()).isEqualTo(3);
        assertThat(runtime.connection("A")).contains(
                new MemberConnection("socket-A", 1, ConnectionState.CLOSED)
        );

        List<GameEvent> remainingEvents = playPassivelyToEnd(runtime, leave);

        assertThat(runtime.summary().phase()).isEqualTo(GamePhase.ROUND_END);
        assertThat(runtime.summary().playerCount()).isEqualTo(2);
        assertThat(runtime.connection("A")).isEmpty();
        assertThat(remainingEvents).extracting(GameEvent::type).contains(GameEventType.PLAYER_LEFT);
    }

    @Test
    void allInPlayerWhoVoluntarilyLeavesRemainsEligibleUntilSettlement() {
        StartedRoom started = startedRoom();
        RoomRuntime runtime = started.runtime();
        GameSnapshot actor = started.startResult().requesterSnapshot();

        RoomExecutionResult allIn = runtime.act(action("all-in-A", "A", actor, ActionType.ALL_IN));
        RoomExecutionResult leave = runtime.leave("leave-all-in-A", "A", "socket-A", 1);
        GameSnapshot observer = runtime.requestSnapshot("B", "socket-B", 1);

        assertThat(observer.players()).filteredOn(player -> player.id().equals("A")).singleElement()
                .satisfies(player -> {
                    assertThat(player.connectionStatus()).isEqualTo(ConnectionStatus.DISCONNECTED);
                    assertThat(player.seatStatus()).isEqualTo(SeatStatus.SEATED);
                    assertThat(player.handStatus()).isEqualTo(HandStatus.ALL_IN);
                    assertThat(player.inHand()).isTrue();
                    assertThat(player.canAct()).isFalse();
                });
        assertThat(runtime.summary().playerCount()).isEqualTo(3);

        List<GameEvent> remainingEvents = playPassivelyToEnd(runtime, allIn);

        assertThat(runtime.summary().phase()).isEqualTo(GamePhase.ROUND_END);
        assertThat(runtime.summary().playerCount()).isEqualTo(2);
        assertThat(remainingEvents).extracting(GameEvent::type).contains(GameEventType.PLAYER_LEFT);
    }

    @Test
    void registryAndServicesKeepControllersAwayFromGameSession() {
        RoomRegistry registry = new RoomRegistry();
        RoomService roomService = new RoomService(registry, java.time.Clock.systemUTC());
        List<RoomDelivery> delivered = new java.util.concurrent.CopyOnWriteArrayList<>();
        RoomEventDispatcher dispatcher = new RoomEventDispatcher(
                registry,
                (roomId, delivery) -> delivered.add(delivery),
                Runnable::run
        );
        GameApplicationService games = new GameApplicationService(registry, dispatcher);

        RoomSummary created = roomService.createRoom("service-room", "Service Room", CONFIG, 55L);
        RoomExecutionResult joined = games.join(
                "service-room", "join-service", "A", "Alice", "socket-service");

        assertThat(created.roomId()).isEqualTo("service-room");
        assertThat(joined.requesterSnapshot()).isNotNull();
        assertThat(delivered).isNotEmpty();
        assertThat(dispatcher.isIdle("service-room")).isTrue();
        assertThat(roomService.listRooms()).singleElement()
                .satisfies(room -> assertThat(room.playerCount()).isEqualTo(1));
        assertThatThrownBy(() -> roomService.removeEmptyRoom("service-room"))
                .isInstanceOfSatisfying(RoomApplicationException.class,
                        error -> assertThat(error.code()).isEqualTo(RoomApplicationErrorCode.ROOM_NOT_EMPTY));
    }

    @Test
    void dispatcherContinuesInOrderAfterOneDeliveryFails() {
        RoomRegistry registry = new RoomRegistry();
        registry.create(new RoomMetadata("dispatch-room", "Dispatch", Instant.EPOCH), CONFIG, 19L);
        List<RoomDelivery> delivered = new java.util.concurrent.CopyOnWriteArrayList<>();
        AtomicInteger attempts = new AtomicInteger();
        RoomEventDispatcher dispatcher = new RoomEventDispatcher(
                registry,
                (roomId, delivery) -> {
                    if (attempts.incrementAndGet() == 1) throw new IllegalStateException("simulated send failure");
                    delivered.add(delivery);
                },
                Runnable::run
        );
        GameApplicationService games = new GameApplicationService(registry, dispatcher);

        games.join("dispatch-room", "join-A", "A", "A", "socket-A");
        games.join("dispatch-room", "join-B", "B", "B", "socket-B");

        assertThat(attempts).hasValue(5);
        assertThat(delivered).hasSize(4);
        assertThat(delivered.get(0)).isInstanceOf(RoomDelivery.Snapshot.class);
        assertThat(delivered.get(1)).isInstanceOfSatisfying(RoomDelivery.Snapshot.class,
                snapshot -> assertThat(snapshot.playerId()).isEqualTo("A"));
        assertThat(delivered.get(2)).isInstanceOfSatisfying(RoomDelivery.Events.class,
                events -> assertThat(events.events()).extracting(GameEvent::type)
                        .containsExactly(GameEventType.PLAYER_JOINED));
        assertThat(delivered.get(3)).isInstanceOfSatisfying(RoomDelivery.Snapshot.class,
                snapshot -> assertThat(snapshot.playerId()).isEqualTo("B"));
        assertThat(dispatcher.isIdle("dispatch-room")).isTrue();
    }

    @Test
    void roomRemovalWaitsForDeliveryDrainAndAtomicallyRejectsLateJoin() {
        RoomRegistry registry = new RoomRegistry();
        RoomRuntime runtime = registry.create(
                new RoomMetadata("closing-room", "Closing", Instant.EPOCH), CONFIG, 21L);
        runtime.join("join-A", "A", "A", "socket-A");
        runtime.leave("leave-A", "A", "socket-A", 1);

        assertThatThrownBy(() -> registry.removeEmpty("closing-room"))
                .isInstanceOfSatisfying(RoomApplicationException.class,
                        error -> assertThat(error.code()).isEqualTo(RoomApplicationErrorCode.ROOM_NOT_EMPTY));

        runtime.drainOutbox();
        registry.removeEmpty("closing-room");

        assertThat(registry.find("closing-room")).isEmpty();
        assertThatThrownBy(() -> runtime.join("late-join", "B", "B", "socket-B"))
                .isInstanceOfSatisfying(RoomApplicationException.class,
                        error -> assertThat(error.code()).isEqualTo(RoomApplicationErrorCode.ROOM_CLOSED));
    }

    private static final String ROOM_ID = "room-runtime";

    private static StartedRoom startedRoom() {
        RoomRuntime runtime = runtime(128, 128, 128);
        runtime.join("join-A", "A", "A", "socket-A");
        runtime.join("join-B", "B", "B", "socket-B");
        runtime.join("join-C", "C", "C", "socket-C");
        runtime.setReady("ready-A", "A", "socket-A", 1, true);
        runtime.setReady("ready-B", "B", "socket-B", 1, true);
        runtime.setReady("ready-C", "C", "socket-C", 1, true);
        RoomExecutionResult start = runtime.startGame(new StartGameCommand(
                ROOM_ID, "start-1", "A", "socket-A", 1, 0));
        return new StartedRoom(runtime, start);
    }

    private static RoomRuntime runtime(int replay, int commands, int outbox) {
        return new RoomRuntime(
                new RoomMetadata(ROOM_ID, "Runtime Test", Instant.EPOCH),
                CONFIG,
                42L,
                replay,
                commands,
                outbox
        );
    }

    private static PlayerActionCommand action(
            String commandId,
            String playerId,
            GameSnapshot snapshot,
            ActionType action
    ) {
        return new PlayerActionCommand(
                ROOM_ID,
                commandId,
                playerId,
                "socket-" + playerId,
                1,
                snapshot.handId(),
                snapshot.turnId(),
                action,
                0
        );
    }

    private static Object raceAction(
            RoomRuntime runtime,
            PlayerActionCommand command,
            CountDownLatch ready,
            CountDownLatch go
    ) throws InterruptedException {
        ready.countDown();
        go.await();
        try {
            return runtime.act(command);
        } catch (IllegalActionException error) {
            return error.code();
        }
    }

    private static void assertStaleConnection(org.assertj.core.api.ThrowableAssert.ThrowingCallable command) {
        assertThatThrownBy(command)
                .isInstanceOfSatisfying(RoomApplicationException.class,
                        error -> assertThat(error.code()).isEqualTo(RoomApplicationErrorCode.STALE_CONNECTION));
    }

    private static List<GameEvent> playPassivelyToEnd(RoomRuntime runtime, RoomExecutionResult initial) {
        RoomExecutionResult result = initial;
        List<GameEvent> events = new ArrayList<>(initial.events());
        AtomicInteger commands = new AtomicInteger(100);
        int guard = 100;
        while (runtime.summary().phase() != GamePhase.ROUND_END && guard-- > 0) {
            String actorId = result.events().stream()
                    .filter(event -> event.type() == GameEventType.TURN_CHANGED)
                    .reduce((first, second) -> second)
                    .map(GameEvent::playerId)
                    .orElseThrow(() -> new AssertionError("missing current actor event"));
            MemberConnection connection = runtime.connection(actorId).orElseThrow();
            GameSnapshot actor = runtime.requestSnapshot(
                    actorId, connection.connectionId(), connection.epoch());
            ActionType passive = actor.legalActions().contains(ActionType.CHECK)
                    ? ActionType.CHECK
                    : ActionType.CALL;
            result = runtime.act(new PlayerActionCommand(
                    ROOM_ID,
                    "passive-" + commands.incrementAndGet(),
                    actorId,
                    connection.connectionId(),
                    connection.epoch(),
                    actor.handId(),
                    actor.turnId(),
                    passive,
                    0
            ));
            events.addAll(result.events());
        }
        assertThat(guard).as("hand must terminate").isPositive();
        return List.copyOf(events);
    }

    private record StartedRoom(RoomRuntime runtime, RoomExecutionResult startResult) {
    }
}
