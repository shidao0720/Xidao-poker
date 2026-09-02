package com.xidao.poker.engine.arena;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LongRunningArenaSimulationTest {

    @Test
    void hundredsOfRoundsPreserveArenaInvariants() {
        ArenaConfig config = new ArenaConfig(2, 400, 300, 20, 4,
                160, 100, 2.5, 320, 400, 5_000, 5);
        ArenaMap map = new ArenaMap(List.of(), List.of(
                new ArenaSpawn(100, 150, 0),
                new ArenaSpawn(300, 150, Math.PI)
        ));
        ArenaSession session = new ArenaSession("arena_soak", "Soak", config, map);
        session.addPlayer("p1", "One", "default");
        session.addPlayer("p2", "Two", "azure");
        long now = 1_000;
        long inputSequence = 0;
        session.setReady("p1", true);
        session.setReady("p2", true);
        session.startRound("p1", now);

        for (int round = 1; round <= 300; round++) {
            session.acceptInput("p1", new VehicleInput(++inputSequence,
                    false, false, false, false, true));

            for (int step = 0; step < 30 && session.phase() == ArenaPhase.RUNNING; step++) {
                now += 50;
                session.tick(now);
                assertSpatialInvariants(session.snapshot(), config);
            }

            ArenaSnapshot settled = session.snapshot();
            assertThat(settled.phase()).as("round %s settles", round).isEqualTo(ArenaPhase.ROUND_OVER);
            assertThat(settled.winnerId()).isEqualTo("p1");
            assertThat(settled.roundId()).isEqualTo(round);
            now = settled.roundEndsAt();
            if (round < 300) {
                session.tick(now);
                assertThat(session.phase()).isEqualTo(ArenaPhase.RUNNING);
                assertThat(session.snapshot().projectiles()).isEmpty();
            }
        }

        ArenaPlayerSnapshot winner = session.snapshot().players().stream()
                .filter(player -> player.id().equals("p1")).findFirst().orElseThrow();
        assertThat(winner.wins()).isEqualTo(300);
        assertThat(session.memberCount()).isEqualTo(2);
    }

    private static void assertSpatialInvariants(ArenaSnapshot snapshot, ArenaConfig config) {
        for (ArenaPlayerSnapshot player : snapshot.players()) {
            assertThat(player.x()).isBetween(config.vehicleRadius(), config.width() - config.vehicleRadius());
            assertThat(player.y()).isBetween(config.vehicleRadius(), config.height() - config.vehicleRadius());
        }
        for (ArenaProjectileSnapshot projectile : snapshot.projectiles()) {
            assertThat(projectile.x()).isBetween(config.projectileRadius(), config.width() - config.projectileRadius());
            assertThat(projectile.y()).isBetween(config.projectileRadius(), config.height() - config.projectileRadius());
            assertThat(projectile.bounces()).isGreaterThanOrEqualTo(0);
        }
        List<ArenaPlayerSnapshot> alive = snapshot.players().stream()
                .filter(player -> player.lifeState() == ArenaLifeState.ALIVE).toList();
        for (int left = 0; left < alive.size(); left++) {
            for (int right = left + 1; right < alive.size(); right++) {
                double dx = alive.get(left).x() - alive.get(right).x();
                double dy = alive.get(left).y() - alive.get(right).y();
                assertThat(dx * dx + dy * dy)
                        .isGreaterThanOrEqualTo(Math.pow(config.vehicleRadius() * 2, 2) - 0.001);
            }
        }
    }
}
