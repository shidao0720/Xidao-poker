package com.xidao.poker.engine.arena;

public record ArenaSkillSnapshot(
        long id,
        ArenaSkillType type,
        double x,
        double y,
        long expiresAt
) { }
