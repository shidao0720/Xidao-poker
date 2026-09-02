package com.xidao.poker.application.arena;

import com.xidao.poker.engine.arena.ArenaSession;
import com.xidao.poker.engine.arena.VehicleInput;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArenaRoomRuntimeTest {
    private static final Instant NOW = Instant.parse("2026-09-01T08:00:00Z");

    @Test
    void mutationRequestIdsAreDeduplicated() {
        List<ArenaUpdate> broadcasts = new ArrayList<>();
        ArenaRoomRuntime runtime = runtime((roomId, update) -> broadcasts.add(update));
        joinDuel(runtime);

        ArenaUpdate first = runtime.ready("ready-p1", "p1", 1, true);
        ArenaUpdate duplicate = runtime.ready("ready-p1", "p1", 1, false);

        assertThat(first.duplicate()).isFalse();
        assertThat(duplicate.duplicate()).isTrue();
        assertThat(duplicate.sequence()).isEqualTo(first.sequence());
        assertThat(player(duplicate, "p1").ready()).isTrue();
        assertThat(broadcasts).hasSize(3);
    }

    @Test
    void inputUsesBothRequestIdAndInputSequenceForDeduplication() {
        ArenaRoomRuntime runtime = runtime(ArenaEventSink.noOp());
        joinDuel(runtime);
        runtime.ready("ready-p1", "p1", 1, true);
        runtime.ready("ready-p2", "p2", 1, true);
        runtime.start("start-001", "p1", 1);

        ArenaUpdate first = runtime.input("input-001", "p1", 1,
                new VehicleInput(1, true, false, false, false, false));
        ArenaUpdate duplicate = runtime.input("input-001", "p1", 1,
                VehicleInput.idle(2));

        assertThat(first.duplicate()).isFalse();
        assertThat(duplicate.duplicate()).isTrue();
        assertThat(duplicate.sequence()).isEqualTo(first.sequence());
    }

    @Test
    void staleDisconnectTimerCannotDisconnectAReplacementConnection() {
        ArenaRoomRuntime runtime = runtime(ArenaEventSink.noOp());
        joinDuel(runtime);
        runtime.disconnect("p1", 1);
        runtime.join("p1", "One", "azure", 2);

        ArenaUpdate stale = runtime.disconnect("p1", 1);

        assertThat(stale.duplicate()).isTrue();
        assertThat(player(stale, "p1").connected()).isTrue();
    }

    @Test
    void reconnectPendingMembersPreventRoomCleanupAndTtlStartsAfterRemoval() {
        ArenaRoomRuntime runtime = runtime(ArenaEventSink.noOp());
        runtime.join("p1", "One", "default", 1);
        runtime.disconnect("p1", 1);

        assertThat(runtime.removable(NOW.plus(Duration.ofDays(1)), Duration.ofSeconds(45))).isFalse();

        runtime.expireDisconnect("p1", 1);
        assertThat(runtime.removable(NOW.plusSeconds(44), Duration.ofSeconds(45))).isFalse();
        assertThat(runtime.removable(NOW.plusSeconds(45), Duration.ofSeconds(45))).isTrue();
    }

    private static ArenaRoomRuntime runtime(ArenaEventSink sink) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new ArenaRoomRuntime(new ArenaSession("arena_runtime", "Runtime", 2), sink, clock);
    }

    private static void joinDuel(ArenaRoomRuntime runtime) {
        runtime.join("p1", "One", "default", 1);
        runtime.join("p2", "Two", "azure", 1);
    }

    private static com.xidao.poker.engine.arena.ArenaPlayerSnapshot player(ArenaUpdate update, String id) {
        return update.snapshot().players().stream().filter(player -> player.id().equals(id)).findFirst().orElseThrow();
    }
}
