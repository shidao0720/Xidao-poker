package com.xidao.poker.web.arena;

import com.xidao.poker.application.arena.ArenaUpdate;
import com.xidao.poker.web.protocol.ProtocolCompatibility;

public record ArenaServerEnvelope(
        String type,
        String roomId,
        Long sequence,
        Object payload,
        int protocolVersion,
        String buildVersion
) {
    public static ArenaServerEnvelope snapshot(String roomId, ArenaUpdate update) {
        return new ArenaServerEnvelope("ARENA_SNAPSHOT", roomId, update.sequence(), update.snapshot(),
                ProtocolCompatibility.CURRENT_PROTOCOL_VERSION, ProtocolCompatibility.currentBuildVersion());
    }

    public static ArenaServerEnvelope ready(ArenaSocketIdentity identity, ArenaUpdate update) {
        return new ArenaServerEnvelope("ARENA_CONNECTION_READY", identity.roomId(), update.sequence(),
                new ConnectionPayload(identity.playerId(), identity.connectionEpoch(), identity.resumeToken()),
                ProtocolCompatibility.CURRENT_PROTOCOL_VERSION, ProtocolCompatibility.currentBuildVersion());
    }

    public static ArenaServerEnvelope error(String roomId, String message) {
        return new ArenaServerEnvelope("ARENA_ERROR", roomId, null, new ErrorPayload(message),
                ProtocolCompatibility.CURRENT_PROTOCOL_VERSION, ProtocolCompatibility.currentBuildVersion());
    }

    public static ArenaServerEnvelope pong(String roomId) {
        return new ArenaServerEnvelope("ARENA_PONG", roomId, null, java.util.Map.of(),
                ProtocolCompatibility.CURRENT_PROTOCOL_VERSION, ProtocolCompatibility.currentBuildVersion());
    }

    public record ConnectionPayload(String playerId, long connectionEpoch, String resumeToken) { }
    public record ErrorPayload(String message) { }
}
