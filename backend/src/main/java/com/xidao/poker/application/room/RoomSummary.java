package com.xidao.poker.application.room;

import com.xidao.poker.engine.game.GamePhase;

public record RoomSummary(
        String roomId,
        String roomName,
        GamePhase phase,
        int playerCount,
        int connectedCount,
        int maxPlayers
) {
}
