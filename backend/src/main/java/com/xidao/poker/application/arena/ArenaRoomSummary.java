package com.xidao.poker.application.arena;

import com.xidao.poker.engine.arena.ArenaPhase;

import java.time.Instant;

public record ArenaRoomSummary(
        String roomId,
        String roomName,
        ArenaPhase phase,
        int players,
        int connectedPlayers,
        int maxPlayers,
        Instant createdAt
) { }
