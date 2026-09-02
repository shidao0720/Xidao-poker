package com.xidao.poker.web.arena;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xidao.poker.application.arena.ArenaEventSink;
import com.xidao.poker.application.arena.ArenaUpdate;

public final class ArenaEventWebSocketSink implements ArenaEventSink {
    private final ArenaConnectionRegistry connections;
    private final ObjectMapper objectMapper;

    public ArenaEventWebSocketSink(ArenaConnectionRegistry connections, ObjectMapper objectMapper) {
        this.connections = connections;
        this.objectMapper = objectMapper;
    }

    @Override
    public void broadcast(String roomId, ArenaUpdate update) {
        try {
            connections.broadcast(roomId, objectMapper.writeValueAsString(ArenaServerEnvelope.snapshot(roomId, update)));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("arena snapshot serialization failed", error);
        }
    }
}
