package com.xidao.poker.engine.arena;

public record ArenaProjectileSnapshot(
        long id,
        String ownerId,
        double x,
        double y,
        double velocityX,
        double velocityY,
        int bounces
) { }
