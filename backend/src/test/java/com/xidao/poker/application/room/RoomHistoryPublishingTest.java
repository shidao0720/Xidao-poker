package com.xidao.poker.application.room;

import com.xidao.poker.application.command.PlayerActionCommand;
import com.xidao.poker.application.command.StartGameCommand;
import com.xidao.poker.application.history.CompletedHandArchive;
import com.xidao.poker.application.history.HistoryPublishResult;
import com.xidao.poker.engine.action.ActionType;
import com.xidao.poker.engine.game.GameConfig;
import com.xidao.poker.engine.snapshot.GameSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class RoomHistoryPublishingTest {
    private static final GameConfig CONFIG = new GameConfig(5, 10, 100, 2);
    private static final Instant NOW = Instant.parse("2026-08-27T02:00:00Z");

    @Test
    void handEndedProducesOneImmutableArchiveAndDuplicateCommandProducesNone() {
        RoomRuntime runtime = new RoomRuntime(
                new RoomMetadata("archive-room", "Archive Room", NOW.minusSeconds(60)),
                CONFIG,
                42L,
                32,
                32,
                32,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        Started started = start(runtime);

        RoomExecutionResult ended = fold(runtime, started);
        RoomExecutionResult duplicate = fold(runtime, started);

        assertThat(ended.completedHands()).singleElement().satisfies(archive -> {
            assertThat(archive.gameId()).isEqualTo("archive-room");
            assertThat(archive.roomName()).isEqualTo("Archive Room");
            assertThat(archive.startedAt()).isEqualTo(NOW);
            assertThat(archive.endedAt()).isEqualTo(NOW);
            assertThat(archive.hand().players()).hasSize(2);
        });
        assertThat(duplicate.duplicate()).isTrue();
        assertThat(duplicate.completedHands()).isEmpty();
    }

    @Test
    void applicationServiceQueuesCompletedHandWithoutGivingRepositoryAccessToRoomLock() {
        RoomRegistry registry = new RoomRegistry(Clock.fixed(NOW, ZoneOffset.UTC));
        registry.create(new RoomMetadata("archive-room", "Archive Room", NOW.minusSeconds(60)), CONFIG, 42L);
        RoomEventDispatcher dispatcher = new RoomEventDispatcher(registry, (room, delivery) -> { }, Runnable::run);
        List<CompletedHandArchive> published = new ArrayList<>();
        GameApplicationService games = new GameApplicationService(registry, dispatcher, archive -> {
            published.add(archive);
            return HistoryPublishResult.ACCEPTED;
        });

        games.join("archive-room", "join-A", "A", "Alice", "socket-A");
        games.join("archive-room", "join-B", "B", "Bob", "socket-B");
        games.setReady("archive-room", "ready-A", "A", "socket-A", 1, true);
        games.setReady("archive-room", "ready-B", "B", "socket-B", 1, true);
        RoomExecutionResult start = games.startGame(new StartGameCommand(
                "archive-room", "start", "A", "socket-A", 1, 0));
        GameSnapshot snapshot = start.requesterSnapshot();
        String actor = playerAt(snapshot, snapshot.currentActorSeat());
        games.act(new PlayerActionCommand(
                "archive-room", "fold", actor, "socket-" + actor, 1,
                snapshot.handId(), snapshot.turnId(), ActionType.FOLD, 0));

        assertThat(published).singleElement()
                .satisfies(archive -> assertThat(archive.handId()).isEqualTo(1));
    }

    @Test
    void historyPublisherFailureCannotTurnACommittedHandIntoACommandFailure() {
        RoomRegistry registry = new RoomRegistry(Clock.fixed(NOW, ZoneOffset.UTC));
        registry.create(new RoomMetadata("archive-room", "Archive Room", NOW.minusSeconds(60)), CONFIG, 42L);
        RoomEventDispatcher dispatcher = new RoomEventDispatcher(registry, (room, delivery) -> { }, Runnable::run);
        GameApplicationService games = new GameApplicationService(registry, dispatcher, archive -> {
            throw new IllegalStateException("simulated history outage");
        });

        games.join("archive-room", "join-A", "A", "Alice", "socket-A");
        games.join("archive-room", "join-B", "B", "Bob", "socket-B");
        games.setReady("archive-room", "ready-A", "A", "socket-A", 1, true);
        games.setReady("archive-room", "ready-B", "B", "socket-B", 1, true);
        RoomExecutionResult start = games.startGame(new StartGameCommand(
                "archive-room", "start", "A", "socket-A", 1, 0));
        GameSnapshot snapshot = start.requesterSnapshot();
        String actor = playerAt(snapshot, snapshot.currentActorSeat());

        assertThatCode(() -> games.act(new PlayerActionCommand(
                "archive-room", "fold", actor, "socket-" + actor, 1,
                snapshot.handId(), snapshot.turnId(), ActionType.FOLD, 0
        ))).doesNotThrowAnyException();
        assertThat(registry.require("archive-room").summary().phase().name()).isEqualTo("ROUND_END");
    }

    private static Started start(RoomRuntime runtime) {
        runtime.join("join-A", "A", "Alice", "socket-A");
        runtime.join("join-B", "B", "Bob", "socket-B");
        runtime.setReady("ready-A", "A", "socket-A", 1, true);
        runtime.setReady("ready-B", "B", "socket-B", 1, true);
        RoomExecutionResult result = runtime.startGame(new StartGameCommand(
                "archive-room", "start", "A", "socket-A", 1, 0));
        GameSnapshot snapshot = result.requesterSnapshot();
        return new Started(snapshot, playerAt(snapshot, snapshot.currentActorSeat()));
    }

    private static RoomExecutionResult fold(RoomRuntime runtime, Started started) {
        return runtime.act(new PlayerActionCommand(
                "archive-room", "fold", started.actor(), "socket-" + started.actor(), 1,
                started.snapshot().handId(), started.snapshot().turnId(), ActionType.FOLD, 0));
    }

    private static String playerAt(GameSnapshot snapshot, int seat) {
        return snapshot.players().stream()
                .filter(player -> player.seat() == seat)
                .findFirst()
                .orElseThrow()
                .id();
    }

    private record Started(GameSnapshot snapshot, String actor) {
    }
}
