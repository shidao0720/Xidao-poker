package com.xidao.poker.engine.arena;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArenaSessionTest {

    @Test
    void supportsTenPlayersAndRejectsTheEleventh() {
        ArenaSession session = new ArenaSession("arena_test", "Ten Player Room", 10);

        for (int index = 0; index < 10; index++) {
            session.addPlayer("p" + index, "Player " + index, "default");
        }

        assertThat(session.memberCount()).isEqualTo(10);
        assertThatThrownBy(() -> session.addPlayer("p10", "Player 10", "default"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("full");
    }

    @Test
    void acceptsUnicodeAccountPlayerIds() {
        ArenaSession session = new ArenaSession("arena_unicode", "Unicode", 2, 11L);

        session.addPlayer("士道", "士道", "god");

        assertThat(player(session, "士道").name()).isEqualTo("士道");
    }

    @Test
    void onlyOwnerCanStartAndEveryConnectedPlayerMustBeReady() {
        ArenaSession session = duelSession();
        session.setReady("p1", true);

        assertThatThrownBy(() -> session.startRound("p2", 1_000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("owner");
        assertThatThrownBy(() -> session.startRound("p1", 1_000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ready");

        session.setReady("p2", true);
        session.startRound("p1", 1_000);

        assertThat(session.phase()).isEqualTo(ArenaPhase.RUNNING);
        assertThat(session.snapshot().players())
                .allMatch(player -> player.lifeState() == ArenaLifeState.ALIVE);
    }

    @Test
    void removingAnActiveParticipantCannotLeaveTheRoundStuck() {
        ArenaSession session = startedDuel();

        session.removePlayer("p2", 1_200);

        assertThat(session.phase()).isEqualTo(ArenaPhase.ROUND_OVER);
        assertThat(session.snapshot().winnerId()).isEqualTo("p1");
    }

    @Test
    void vehiclesCannotDriveThroughEachOther() {
        ArenaConfig config = compactConfig();
        ArenaMap map = new ArenaMap(List.of(), List.of(
                new ArenaSpawn(100, 150, 0),
                new ArenaSpawn(200, 150, Math.PI)
        ));
        ArenaSession session = new ArenaSession("arena_collision", "Collision", config, map);
        addDuelPlayers(session);
        start(session);
        session.acceptInput("p1", new VehicleInput(1, true, false, false, false, false));

        for (long time = 1_050; time <= 2_000; time += 50) session.tick(time);

        ArenaPlayerSnapshot first = player(session, "p1");
        ArenaPlayerSnapshot second = player(session, "p2");
        assertThat(second.x() - first.x()).isGreaterThanOrEqualTo(config.vehicleRadius() * 2 - 0.001);
    }

    @Test
    void projectileHitEliminatesPlayerAndSettlesTheRound() {
        ArenaConfig config = compactConfig();
        ArenaMap map = new ArenaMap(List.of(), List.of(
                new ArenaSpawn(100, 150, 0),
                new ArenaSpawn(300, 150, Math.PI)
        ));
        ArenaSession session = new ArenaSession("arena_shot", "Shot", config, map);
        addDuelPlayers(session);
        start(session);
        session.acceptInput("p1", new VehicleInput(1, false, false, false, false, true));

        for (long time = 1_050; time <= 2_000 && session.phase() == ArenaPhase.RUNNING; time += 50) {
            session.tick(time);
        }

        assertThat(session.phase()).isEqualTo(ArenaPhase.ROUND_OVER);
        assertThat(session.snapshot().winnerId()).isEqualTo("p1");
        assertThat(player(session, "p2").lifeState()).isEqualTo(ArenaLifeState.ELIMINATED);
        assertThat(player(session, "p1").wins()).isEqualTo(1);
        assertThat(session.snapshot().eliminations()).singleElement().satisfies(elimination -> {
            assertThat(elimination.victimId()).isEqualTo("p2");
            assertThat(elimination.attackerId()).isEqualTo("p1");
            assertThat(elimination.roundId()).isEqualTo(1);
        });
    }

    @Test
    void eachPlayerHasFiveProjectilesAndAnExpiredShotImmediatelyRestoresOneAmmo() {
        ArenaConfig config = new ArenaConfig(2, 600, 400, 20, 4,
                160, 100, 2.5, 200, 100, 5_000, 5);
        ArenaMap map = new ArenaMap(List.of(), List.of(
                new ArenaSpawn(100, 100, 0),
                new ArenaSpawn(100, 300, Math.PI)
        ));
        ArenaSession session = new ArenaSession("arena_ammo", "Ammo", config, map);
        addDuelPlayers(session);
        start(session);
        session.acceptInput("p1", new VehicleInput(1, false, false, false, false, true));

        for (long time = 1_001; time <= 1_401; time += 100) session.tick(time);

        assertThat(session.snapshot().projectiles()).hasSize(5);
        long firstProjectileId = session.snapshot().projectiles().getFirst().id();
        session.tick(6_000);
        assertThat(session.snapshot().projectiles()).hasSize(5)
                .extracting(ArenaProjectileSnapshot::id).contains(firstProjectileId);

        session.tick(6_001);

        assertThat(session.snapshot().projectiles()).hasSize(5)
                .extracting(ArenaProjectileSnapshot::id).doesNotContain(firstProjectileId);
    }

    @Test
    void wallReflectionChangesTheCurrentSubstepVelocityWithoutConsumingTheProjectile() {
        ArenaConfig config = compactConfig();
        ArenaMap map = new ArenaMap(List.of(new ArenaWall(180, 0, 20, 300)), List.of(
                new ArenaSpawn(120, 150, 0),
                new ArenaSpawn(320, 250, Math.PI)
        ));
        ArenaSession session = new ArenaSession("arena_reflect", "Reflect", config, map);
        addDuelPlayers(session);
        start(session);
        session.acceptInput("p1", new VehicleInput(1, false, false, false, false, true));

        session.tick(1_050);
        session.tick(1_100);

        assertThat(session.snapshot().projectiles()).singleElement().satisfies(projectile -> {
            assertThat(projectile.bounces()).isEqualTo(1);
            assertThat(projectile.velocityX()).isNegative();
        });
    }

    @Test
    void aShotFiredNextToAWallArmsOnReflectionAndCanEliminateItsOwner() {
        ArenaConfig config = compactConfig();
        ArenaMap map = new ArenaMap(List.of(new ArenaWall(124, 0, 20, 300)), List.of(
                new ArenaSpawn(100, 150, 0),
                new ArenaSpawn(300, 240, Math.PI)
        ));
        ArenaSession session = new ArenaSession("arena_self_hit", "Self hit", config, map);
        addDuelPlayers(session);
        start(session);
        session.acceptInput("p1", new VehicleInput(1, false, false, false, false, true));

        session.tick(1_050);

        assertThat(player(session, "p1").lifeState()).isEqualTo(ArenaLifeState.ELIMINATED);
        assertThat(session.snapshot().eliminations()).singleElement().satisfies(elimination -> {
            assertThat(elimination.victimId()).isEqualTo("p1");
            assertThat(elimination.attackerId()).isEqualTo("p1");
        });
    }

    @Test
    void automaticallyStartsTheNextRoundAfterTheEliminationTransition() {
        ArenaConfig config = compactConfig();
        ArenaMap map = new ArenaMap(List.of(), List.of(
                new ArenaSpawn(100, 150, 0),
                new ArenaSpawn(300, 150, Math.PI)
        ));
        ArenaSession session = new ArenaSession("arena_auto", "Auto", config, map);
        addDuelPlayers(session);
        start(session);
        session.acceptInput("p1", new VehicleInput(1, false, false, false, false, true));
        for (long time = 1_050; time <= 2_000 && session.phase() == ArenaPhase.RUNNING; time += 50) {
            session.tick(time);
        }

        long nextRoundAt = session.snapshot().roundEndsAt();
        session.tick(nextRoundAt - 1);
        assertThat(session.phase()).isEqualTo(ArenaPhase.ROUND_OVER);
        session.tick(nextRoundAt);

        assertThat(session.phase()).isEqualTo(ArenaPhase.RUNNING);
        assertThat(session.roundId()).isEqualTo(2);
        assertThat(session.snapshot().winnerId()).isNull();
        assertThat(session.snapshot().eliminations()).isEmpty();
        assertThat(session.snapshot().players()).allMatch(player -> player.lifeState() == ArenaLifeState.ALIVE);
        assertThat(player(session, "p1").wins()).isEqualTo(1);
    }

    @Test
    void staleInputSequenceCannotReplaceNewerInput() {
        ArenaSession session = startedDuel();
        double originalX = player(session, "p1").x();
        double originalY = player(session, "p1").y();
        session.acceptInput("p1", new VehicleInput(2, true, false, false, false, false));
        session.acceptInput("p1", VehicleInput.idle(1));

        session.tick(1_100);

        ArenaPlayerSnapshot moved = player(session, "p1");
        assertThat(Math.hypot(moved.x() - originalX, moved.y() - originalY)).isGreaterThan(0.1);
    }

    @Test
    void specialSkillAppearsEveryTwentySecondsAtASafePosition() {
        ArenaConfig config = compactConfig();
        ArenaMap map = new ArenaMap(List.of(), List.of(
                new ArenaSpawn(80, 80, 0),
                new ArenaSpawn(320, 220, Math.PI)
        ));
        ArenaSession session = new ArenaSession("arena_skill", "Skill", config, map, 72L);
        addDuelPlayers(session);
        start(session);

        session.tick(20_999);
        assertThat(session.snapshot().skills()).isEmpty();
        session.tick(21_000);

        ArenaSnapshot snapshot = session.snapshot();
        assertThat(snapshot.skills()).hasSize(1);
        assertThat(snapshot.skillSpawnInMillis()).isEqualTo(20_000);
        ArenaSkillSnapshot skill = snapshot.skills().getFirst();
        assertThat(skill.x()).isBetween(20.0, config.width() - 20.0);
        assertThat(skill.y()).isBetween(20.0, config.height() - 20.0);
        assertThat(skill.expiresAt()).isGreaterThan(21_000);
    }

    @Test
    void generatedRoomReceivesANewMazeForTheNextRound() {
        ArenaSession session = new ArenaSession("arena_random", "Random", 2, 9182L);
        addDuelPlayers(session);
        start(session);
        List<ArenaWall> firstMaze = session.snapshot().walls();
        session.removePlayer("p2", 1_200);
        session.tick(session.snapshot().roundEndsAt());
        session.addPlayer("p2", "Two", "azure");
        session.setReady("p1", true);
        session.setReady("p2", true);
        session.startRound("p1", 5_000);

        assertThat(session.snapshot().walls()).isNotEqualTo(firstMaze);
        assertThat(session.snapshot().walls()).hasSizeGreaterThanOrEqualTo(38);
    }

    private static ArenaSession duelSession() {
        ArenaSession session = new ArenaSession("arena_test", "Duel", 2, 12_345L);
        addDuelPlayers(session);
        return session;
    }

    private static void addDuelPlayers(ArenaSession session) {
        session.addPlayer("p1", "One", "default");
        session.addPlayer("p2", "Two", "azure");
    }

    private static ArenaSession startedDuel() {
        ArenaSession session = duelSession();
        start(session);
        return session;
    }

    private static void start(ArenaSession session) {
        session.setReady("p1", true);
        session.setReady("p2", true);
        session.startRound("p1", 1_000);
    }

    private static ArenaPlayerSnapshot player(ArenaSession session, String playerId) {
        return session.snapshot().players().stream()
                .filter(player -> player.id().equals(playerId))
                .findFirst()
                .orElseThrow();
    }

    private static ArenaConfig compactConfig() {
        return new ArenaConfig(2, 400, 300, 20, 4,
                160, 100, 2.5, 320, 400, 5_000, 5);
    }
}
