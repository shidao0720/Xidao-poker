package com.xidao.poker.application.arena;

import com.xidao.poker.engine.arena.VehicleInput;

import java.util.List;

public final class ArenaApplicationService {
    private final ArenaRoomRegistry rooms;

    public ArenaApplicationService(ArenaRoomRegistry rooms) {
        this.rooms = rooms;
    }

    public ArenaRoomSummary create(String roomName, int maxPlayers) { return rooms.create(roomName, maxPlayers); }
    public List<ArenaRoomSummary> list() { return rooms.list(); }
    public ArenaUpdate current(String roomId) { return rooms.require(roomId).current(); }

    public ArenaUpdate join(String roomId, String playerId, String name, String avatarKey, long connectionEpoch) {
        return rooms.require(roomId).join(playerId, name, avatarKey, connectionEpoch);
    }

    public ArenaUpdate ready(String roomId, String requestId, String playerId, long epoch, boolean ready) {
        return rooms.require(roomId).ready(requestId, playerId, epoch, ready);
    }

    public ArenaUpdate start(String roomId, String requestId, String playerId, long epoch) {
        return rooms.require(roomId).start(requestId, playerId, epoch);
    }

    public ArenaUpdate input(String roomId, String requestId, String playerId, long epoch, VehicleInput input) {
        return rooms.require(roomId).input(requestId, playerId, epoch, input);
    }

    public ArenaUpdate disconnect(String roomId, String playerId, long epoch) {
        return rooms.require(roomId).disconnect(playerId, epoch);
    }

    public ArenaUpdate expireDisconnect(String roomId, String playerId, long epoch) {
        return rooms.require(roomId).expireDisconnect(playerId, epoch);
    }

    public ArenaUpdate leave(String roomId, String requestId, String playerId, long epoch) {
        return rooms.require(roomId).leave(requestId, playerId, epoch);
    }
}
