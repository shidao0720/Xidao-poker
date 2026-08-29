package com.xidao.poker.application.room;

import com.xidao.poker.application.command.EmptyRoomCleanupCommand;
import com.xidao.poker.engine.game.GameConfig;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class EmptyRoomCleanupTest {
    private static final GameConfig CONFIG = new GameConfig(5, 10, 1_000, 10);
    private static final Duration EMPTY_TTL = Duration.ofSeconds(20);

    @Test
    void removesEveryRoomThatHasStayedEmptyForTwentySeconds() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-27T00:00:00Z"));
        RoomRegistry registry = new RoomRegistry(clock);
        RoomService rooms = new RoomService(registry, clock);
        rooms.createRoom("empty-a", "Empty A", CONFIG, 1L);
        rooms.createRoom("empty-b", "Empty B", CONFIG, 2L);

        clock.advance(Duration.ofSeconds(19));
        assertThat(rooms.removeRoomsEmptyFor(EMPTY_TTL)).isEmpty();
        assertThat(rooms.listRooms()).hasSize(2);

        clock.advance(Duration.ofSeconds(1));
        assertThat(rooms.removeRoomsEmptyFor(EMPTY_TTL)).containsExactly("empty-a", "empty-b");
        assertThat(rooms.listRooms()).isEmpty();
    }

    @Test
    void rejoiningBeforeExpiryResetsTheEmptyRoomDeadline() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-27T00:00:00Z"));
        RoomRegistry registry = new RoomRegistry(clock);
        RoomService rooms = new RoomService(registry, clock);
        RoomRuntime runtime = registry.create(
                new RoomMetadata("rejoin-room", "Rejoin", clock.instant()), CONFIG, 3L);

        runtime.join("join-A", "A", "Alice", "socket-A");
        runtime.drainOutbox();
        runtime.leave("leave-A", "A", "socket-A", 1);
        runtime.drainOutbox();

        clock.advance(Duration.ofSeconds(10));
        runtime.join("join-B", "B", "Bob", "socket-B");
        runtime.drainOutbox();
        clock.advance(Duration.ofSeconds(20));

        assertThat(rooms.removeRoomsEmptyFor(EMPTY_TTL)).isEmpty();
        assertThat(rooms.listRooms()).singleElement()
                .satisfies(room -> assertThat(room.playerCount()).isEqualTo(1));

        runtime.leave("leave-B", "B", "socket-B", 1);
        runtime.drainOutbox();
        clock.advance(Duration.ofSeconds(19));
        assertThat(rooms.removeRoomsEmptyFor(EMPTY_TTL)).isEmpty();

        clock.advance(Duration.ofSeconds(1));
        assertThat(rooms.removeRoomsEmptyFor(EMPTY_TTL)).containsExactly("rejoin-room");
        assertThat(registry.find("rejoin-room")).isEmpty();
    }

    @Test
    void cleanupCommandCapturedBeforeAJoinIsStaleAtExecutionTime() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-27T00:00:00Z"));
        RoomRegistry registry = new RoomRegistry(clock);
        RoomRuntime runtime = registry.create(
                new RoomMetadata("stale-cleanup", "Stale Cleanup", clock.instant()), CONFIG, 4L);
        clock.advance(EMPTY_TTL);
        List<EmptyRoomCleanupCommand> commands = registry.cleanupCommandsDue(clock.instant(), EMPTY_TTL);
        assertThat(commands).singleElement();

        runtime.join("join-A", "A", "Alice", "socket-A");
        runtime.drainOutbox();
        assertThat(registry.executeCleanupTimers(commands, clock.instant())).isEmpty();
        assertThat(registry.find("stale-cleanup")).contains(runtime);
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(current, zone);
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
