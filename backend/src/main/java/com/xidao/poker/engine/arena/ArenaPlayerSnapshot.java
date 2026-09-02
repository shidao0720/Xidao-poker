package com.xidao.poker.engine.arena;

public record ArenaPlayerSnapshot(
        String id,
        String name,
        String avatarKey,
        int seat,
        boolean connected,
        boolean ready,
        ArenaLifeState lifeState,
        double x,
        double y,
        double angle,
        int kills,
        int wins,
        boolean speedBoosted,
        boolean rapidFire,
        boolean shielded
) { }
