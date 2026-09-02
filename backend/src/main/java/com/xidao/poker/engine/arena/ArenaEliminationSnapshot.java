package com.xidao.poker.engine.arena;

public record ArenaEliminationSnapshot(
        long id,
        long roundId,
        String victimId,
        String attackerId,
        long occurredAt
) { }
