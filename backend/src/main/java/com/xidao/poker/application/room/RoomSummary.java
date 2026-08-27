package com.xidao.poker.application.room;

import com.xidao.poker.engine.game.GamePhase;

import java.time.Instant;

public record RoomSummary(
        String roomId,
        String roomName,
        Instant createdAt,
        GamePhase phase,
        int playerCount,
        int connectedCount,
        int smallBlind,
        int bigBlind,
        int buyIn,
        int maxPlayers
) {
}
