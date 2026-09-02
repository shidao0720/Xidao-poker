package com.xidao.poker.web.arena;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xidao.poker.application.arena.ArenaApplicationService;
import com.xidao.poker.application.arena.ArenaUpdate;
import com.xidao.poker.config.PokerNetworkProperties;
import com.xidao.poker.engine.arena.VehicleInput;
import com.xidao.poker.web.protocol.ProtocolCompatibility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Locale;

public final class ArenaWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(ArenaWebSocketHandler.class);
    private final ArenaApplicationService arenas;
    private final ArenaConnectionRegistry connections;
    private final ArenaDisconnectScheduler disconnects;
    private final ObjectMapper objectMapper;
    private final int maximumMessageBytes;

    public ArenaWebSocketHandler(ArenaApplicationService arenas, ArenaConnectionRegistry connections,
                                 ArenaDisconnectScheduler disconnects, ObjectMapper objectMapper,
                                 PokerNetworkProperties properties) {
        this.arenas = arenas;
        this.connections = connections;
        this.disconnects = disconnects;
        this.objectMapper = objectMapper;
        this.maximumMessageBytes = properties.maximumTextMessageBytes();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Object value = session.getAttributes().get(ArenaHandshakeInterceptor.ATTRIBUTE);
        if (!(value instanceof ArenaHandshakeRequest request)) {
            sendAndClose(session, null, "invalid arena handshake");
            return;
        }
        if (!ProtocolCompatibility.protocolMatches(request.protocolVersion())
                || !ProtocolCompatibility.buildMatches(request.buildVersion())) {
            sendAndClose(session, request.roomId(), "refresh the page to match the server version");
            return;
        }
        try {
            ArenaSocketIdentity identity = connections.prepare(session, request);
            ArenaUpdate update = arenas.join(identity.roomId(), identity.playerId(), identity.playerName(),
                    identity.avatarKey(), identity.connectionEpoch());
            send(identity, ArenaServerEnvelope.ready(identity, update));
            send(identity, ArenaServerEnvelope.snapshot(identity.roomId(), update));
            log.info("ARENA_WS_CONNECT roomId={} playerId={} connectionId={} epoch={}",
                    identity.roomId(), identity.playerId(), identity.connectionId(), identity.connectionEpoch());
        } catch (RuntimeException error) {
            sendAndClose(session, request.roomId(), safeMessage(error));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        ArenaSocketIdentity identity = identityFor(session);
        if (identity == null) {
            sendAndClose(session, null, "arena connection is not bound");
            return;
        }
        if (message.getPayloadLength() > maximumMessageBytes) {
            sendAndClose(session, identity.roomId(), "arena message is too large");
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(message.getPayload());
            String type = requiredText(root, "type").toUpperCase(Locale.ROOT);
            String requestId = optionalText(root, "requestId");
            JsonNode payload = root.path("payload");
            switch (type) {
                case "READY" -> arenas.ready(identity.roomId(), requireRequestId(requestId), identity.playerId(),
                        identity.connectionEpoch(), payload.path("ready").asBoolean());
                case "START" -> arenas.start(identity.roomId(), requireRequestId(requestId), identity.playerId(),
                        identity.connectionEpoch());
                case "INPUT" -> arenas.input(identity.roomId(), requireRequestId(requestId), identity.playerId(),
                        identity.connectionEpoch(), new VehicleInput(
                                positiveLong(payload, "sequence"),
                                payload.path("forward").asBoolean(), payload.path("backward").asBoolean(),
                                payload.path("turnLeft").asBoolean(), payload.path("turnRight").asBoolean(),
                                payload.path("fire").asBoolean()));
                case "REQUEST_SNAPSHOT" -> send(identity, ArenaServerEnvelope.snapshot(identity.roomId(), arenas.current(identity.roomId())));
                case "LEAVE" -> {
                    ArenaUpdate update = arenas.leave(identity.roomId(), requireRequestId(requestId), identity.playerId(), identity.connectionEpoch());
                    send(identity, ArenaServerEnvelope.snapshot(identity.roomId(), update));
                    connections.forgetIfCurrent(identity.roomId(), identity.playerId(), identity.connectionEpoch());
                    session.close(CloseStatus.NORMAL);
                }
                case "PING" -> send(identity, ArenaServerEnvelope.pong(identity.roomId()));
                default -> throw new IllegalArgumentException("unknown arena message type");
            }
        } catch (RuntimeException error) {
            send(identity, ArenaServerEnvelope.error(identity.roomId(), safeMessage(error)));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        connections.detach(session.getId()).ifPresent(identity -> {
            try {
                arenas.disconnect(identity.roomId(), identity.playerId(), identity.connectionEpoch());
                disconnects.schedule(identity);
                log.info("ARENA_WS_DISCONNECT roomId={} playerId={} connectionId={} epoch={} closeCode={}",
                        identity.roomId(), identity.playerId(), identity.connectionId(), identity.connectionEpoch(), status.getCode());
            } catch (RuntimeException error) {
                log.warn("ARENA_WS_DISCONNECT_FAILED roomId={} playerId={} epoch={} closeCode={} code={} message={}",
                        identity.roomId(), identity.playerId(), identity.connectionEpoch(), status.getCode(),
                        error.getClass().getSimpleName(), safeMessage(error));
            }
        });
    }

    private ArenaSocketIdentity identityFor(WebSocketSession session) {
        return connections.identity(session.getId()).orElse(null);
    }

    private void send(ArenaSocketIdentity identity, ArenaServerEnvelope envelope) {
        try {
            connections.send(identity, objectMapper.writeValueAsString(envelope));
        } catch (IOException error) {
            throw new IllegalStateException("arena message send failed", error);
        }
    }

    private void sendAndClose(WebSocketSession session, String roomId, String message) {
        try {
            if (session.isOpen()) session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                    ArenaServerEnvelope.error(roomId, message))));
        } catch (IOException ignored) {
        } finally {
            try { if (session.isOpen()) session.close(CloseStatus.POLICY_VIOLATION); } catch (IOException ignored) { }
        }
    }

    private static String requiredText(JsonNode root, String name) {
        String value = optionalText(root, name);
        if (value == null) throw new IllegalArgumentException(name + " is required");
        return value;
    }

    private static String optionalText(JsonNode root, String name) {
        JsonNode value = root.get(name);
        if (value == null || !value.isTextual() || value.asText().isBlank()) return null;
        return value.asText().strip();
    }

    private static String requireRequestId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{8,64}")) throw new IllegalArgumentException("request id is invalid");
        return value;
    }

    private static long positiveLong(JsonNode payload, String name) {
        long value = payload.path(name).asLong(-1);
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    private static String safeMessage(RuntimeException error) {
        return error.getMessage() == null ? "arena command failed" : error.getMessage();
    }
}
