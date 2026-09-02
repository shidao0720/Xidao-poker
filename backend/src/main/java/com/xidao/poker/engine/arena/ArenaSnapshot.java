package com.xidao.poker.engine.arena;

import java.util.List;

public record ArenaSnapshot(
        String roomId,
        String roomName,
        String ownerId,
        ArenaPhase phase,
        long roundId,
        long tick,
        double width,
        double height,
        int maxPlayers,
        int projectileCapacity,
        List<ArenaWall> walls,
        List<ArenaPlayerSnapshot> players,
        List<ArenaProjectileSnapshot> projectiles,
        List<ArenaSkillSnapshot> skills,
        List<ArenaEliminationSnapshot> eliminations,
        long skillSpawnInMillis,
        String winnerId,
        long roundEndsAt
) {
    public ArenaSnapshot {
        walls = List.copyOf(walls);
        players = List.copyOf(players);
        projectiles = List.copyOf(projectiles);
        skills = List.copyOf(skills);
        eliminations = List.copyOf(eliminations);
    }
}
