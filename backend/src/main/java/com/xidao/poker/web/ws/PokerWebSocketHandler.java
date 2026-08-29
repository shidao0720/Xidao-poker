package com.xidao.poker.web.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xidao.poker.application.command.PlayerActionCommand;
import com.xidao.poker.application.command.StartGameCommand;
import com.xidao.poker.application.room.EventReplay;
import com.xidao.poker.application.room.GameApplicationService;
import com.xidao.poker.application.room.RoomApplicationException;
import com.xidao.poker.application.room.RoomExecutionResult;
import com.xidao.poker.config.PokerNetworkProperties;
import com.xidao.poker.engine.action.IllegalActionException;
import com.xidao.poker.web.protocol.ClientEnvelope;
import com.xidao.poker.web.protocol.ClientMessageType;
import com.xidao.poker.web.protocol.PlayerActionPayload;
import com.xidao.poker.web.protocol.ProtocolCompatibility;
import com.xidao.poker.web.protocol.ReadyPayload;
import com.xidao.poker.web.protocol.ReplayEventsPayload;
import com.xidao.poker.web.protocol.ServerEnvelope;
import com.xidao.poker.web.protocol.StartGamePayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * WebSocket 只解析意图并调用 GameApplicationService。任何 room/player/connection 身份都来自握手绑定。
 */
public final class PokerWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(PokerWebSocketHandler.class);
    private static final CloseStatus MESSAGE_TOO_LARGE = new CloseStatus(1009, "message too large");

    private final GameApplicationService games;
    private final WebSocketConnectionRegistry connections;
    private final DisconnectGraceScheduler disconnects;
    private final ObjectMapper objectMapper;
    private final int maximumTextMessageBytes;

    public PokerWebSocketHandler(
            GameApplicationService games,
            WebSocketConnectionRegistry connections,
            DisconnectGraceScheduler disconnects,
            ObjectMapper objectMapper,
            PokerNetworkProperties properties
    ) {
        this.games = games;
        this.connections = connections;
        this.disconnects = disconnects;
        this.objectMapper = objectMapper;
        this.maximumTextMessageBytes = properties.maximumTextMessageBytes();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Object attribute = session.getAttributes().get(PokerHandshakeInterceptor.HANDSHAKE_ATTRIBUTE);
        if (!(attribute instanceof PokerHandshakeRequest request)) {
            sendRawErrorAndClose(session, null, null, "INVALID_HANDSHAKE", "missing connection identity");
            return;
        }

        if (!ProtocolCompatibility.protocolMatches(request.protocolVersion())) {
            sendRawErrorAndClose(
                    session,
                    request.roomId(),
                    null,
                    "PROTOCOL_VERSION_MISMATCH",
                    "client protocol is incompatible with this server"
            );
            return;
        }
        if (!ProtocolCompatibility.buildMatches(request.buildVersion())) {
            sendRawErrorAndClose(
                    session,
                    request.roomId(),
                    null,
                    "BUILD_VERSION_MISMATCH",
                    "refresh the page to load the matching client build"
            );
            return;
        }

        WebSocketConnectionRegistry.PreparedConnection prepared;
        try {
            prepared = connections.prepare(session, request);
        } catch (RuntimeException error) {
            sendRawErrorAndClose(session, request.roomId(), null, errorCode(error), safeMessage(error));
            return;
        }

        SocketIdentity identity = prepared.identity();
        try {
            if (request.reconnecting()) {
                games.reconnect(
                        identity.roomId(),
                        "ws-reconnect-" + identity.connectionId(),
                        identity.playerId(),
                        request.expectedConnectionEpoch(),
                        identity.connectionId()
                );
            } else {
                games.join(
                        identity.roomId(),
                        "ws-join-" + identity.connectionId(),
                        identity.playerId(),
                        identity.playerName(),
                        identity.connectionId()
                );
            }
        } catch (RuntimeException error) {
            try {
                sendBoundError(identity, null, error);
            } finally {
                connections.rollback(prepared);
            }
            return;
        }

        try {
            connections.commit(prepared);
            disconnects.cancel(identity.roomId(), identity.playerId());
            send(identity, ServerEnvelope.connectionReady(
                    identity.roomId(),
                    identity.playerId(),
                    identity.connectionEpoch(),
                    identity.resumeToken()
            ));
            log.info("WS_CONNECT roomId={} playerId={} connectionId={} epoch={} reconnect={}",
                    identity.roomId(), identity.playerId(), identity.connectionId(),
                    identity.connectionEpoch(), request.reconnecting());
        } catch (RuntimeException error) {
            log.warn("WS_CONNECT_DELIVERY_FAILED roomId={} playerId={} connectionId={} epoch={}",
                    identity.roomId(), identity.playerId(), identity.connectionId(),
                    identity.connectionEpoch(), error);
            closeQuietly(session, CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        SocketIdentity identity = connections.currentIdentity(session.getId()).orElse(null);
        if (identity == null) {
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        if (message.getPayload().getBytes(StandardCharsets.UTF_8).length > maximumTextMessageBytes) {
            closeQuietly(session, MESSAGE_TOO_LARGE);
            return;
        }

        ClientEnvelope envelope = null;
        try {
            envelope = objectMapper.readValue(message.getPayload(), ClientEnvelope.class);
            ClientMessageType type = parseType(envelope.type());
            String commandId = type == ClientMessageType.PING
                    ? optionalCommandId(envelope.requestId())
                    : requiredCommandId(envelope.requestId());
            log.debug("WS_MESSAGE_RECEIVED roomId={} playerId={} connectionId={} type={} commandId={}",
                    identity.roomId(), identity.playerId(), identity.connectionId(), type, commandId);
            handle(identity, session, type, commandId, envelope.payload());
        } catch (JsonProcessingException | IllegalArgumentException error) {
            log.debug("WS_MESSAGE_REJECTED roomId={} playerId={} connectionId={} code=INVALID_MESSAGE",
                    identity.roomId(), identity.playerId(), identity.connectionId());
            send(identity, ServerEnvelope.error(
                    identity.roomId(),
                    envelope == null ? null : envelope.requestId(),
                    "INVALID_MESSAGE",
                    "message validation failed"
            ));
        } catch (IllegalActionException | RoomApplicationException error) {
            log.debug("WS_MESSAGE_REJECTED roomId={} playerId={} connectionId={} code={}",
                    identity.roomId(), identity.playerId(), identity.connectionId(), errorCode(error));
            sendBoundError(identity, envelope == null ? null : envelope.requestId(), error);
        } catch (RuntimeException error) {
            log.error("WS_MESSAGE_FAILED roomId={} playerId={} connectionId={} commandId={}",
                    identity.roomId(), identity.playerId(), identity.connectionId(),
                    envelope == null ? null : envelope.requestId(), error);
            send(identity, ServerEnvelope.error(
                    identity.roomId(),
                    envelope == null ? null : envelope.requestId(),
                    "INTERNAL_ERROR",
                    "server could not process the message"
            ));
        }
    }

    private void handle(
            SocketIdentity identity,
            WebSocketSession session,
            ClientMessageType type,
            String commandId,
            JsonNode payload
    ) throws JsonProcessingException {
        RoomExecutionResult result;
        switch (type) {
            case READY -> {
                ReadyPayload ready = payload(payload, ReadyPayload.class);
                result = games.setReady(
                        identity.roomId(), commandId, identity.playerId(), identity.connectionId(),
                        identity.connectionEpoch(), ready.ready()
                );
                send(identity, ServerEnvelope.commandResult(identity.roomId(), commandId, result));
            }
            case START_GAME -> {
                StartGamePayload start = payload(payload, StartGamePayload.class);
                result = games.startGame(new StartGameCommand(
                        identity.roomId(), commandId, identity.playerId(), identity.connectionId(),
                        identity.connectionEpoch(), start.expectedPreviousHandId()
                ));
                send(identity, ServerEnvelope.commandResult(identity.roomId(), commandId, result));
            }
            case PLAYER_ACTION -> {
                PlayerActionPayload action = payload(payload, PlayerActionPayload.class);
                result = games.act(new PlayerActionCommand(
                        identity.roomId(), commandId, identity.playerId(), identity.connectionId(),
                        identity.connectionEpoch(), action.handId(), action.turnId(), action.action(), action.amount()
                ));
                send(identity, ServerEnvelope.commandResult(identity.roomId(), commandId, result));
            }
            case REQUEST_SNAPSHOT -> games.requestSnapshot(
                    identity.roomId(), identity.playerId(), identity.connectionId(), identity.connectionEpoch()
            );
            case REPLAY_EVENTS -> {
                ReplayEventsPayload replayRequest = payload(payload, ReplayEventsPayload.class);
                EventReplay replay = games.replayAfter(
                        identity.roomId(), identity.playerId(), identity.connectionId(),
                        identity.connectionEpoch(), replayRequest.afterSequence()
                );
                if (replay.snapshotRequired()) {
                    games.requestSnapshot(
                            identity.roomId(), identity.playerId(), identity.connectionId(), identity.connectionEpoch()
                    );
                } else {
                    send(identity, ServerEnvelope.replay(identity.roomId(), commandId, replay));
                }
            }
            case LEAVE -> {
                result = games.leave(
                        identity.roomId(), commandId, identity.playerId(), identity.connectionId(),
                        identity.connectionEpoch()
                );
                send(identity, ServerEnvelope.commandResult(identity.roomId(), commandId, result));
                disconnects.cancel(identity.roomId(), identity.playerId());
                connections.forgetMember(
                        identity.roomId(), identity.playerId(), identity.connectionEpoch()
                );
                closeQuietly(session, CloseStatus.NORMAL);
            }
            case PING -> send(identity, ServerEnvelope.pong(identity.roomId(), commandId));
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        connections.currentIdentity(session.getId()).ifPresent(identity ->
                log.warn("WS_TRANSPORT_ERROR roomId={} playerId={} connectionId={} error={}",
                        identity.roomId(), identity.playerId(), identity.connectionId(),
                        exception.getClass().getSimpleName()));
        closeQuietly(session, CloseStatus.SERVER_ERROR);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        connections.detach(session.getId()).ifPresent(identity -> {
            try {
                RoomExecutionResult result = games.disconnect(
                        identity.roomId(),
                        "ws-disconnect-" + identity.connectionId(),
                        identity.playerId(),
                        identity.connectionId(),
                        identity.connectionEpoch()
                );
                if (!result.ignored()) disconnects.schedule(identity);
                log.info("WS_DISCONNECT roomId={} playerId={} connectionId={} epoch={} closeCode={} ignored={}",
                        identity.roomId(), identity.playerId(), identity.connectionId(),
                        identity.connectionEpoch(), status.getCode(), result.ignored());
            } catch (RuntimeException error) {
                log.warn("WS_DISCONNECT_FAILED roomId={} playerId={} connectionId={} epoch={} code={}",
                        identity.roomId(), identity.playerId(), identity.connectionId(),
                        identity.connectionEpoch(), errorCode(error));
            }
        });
    }

    private <T> T payload(JsonNode node, Class<T> type) throws JsonProcessingException {
        if (node == null || node.isNull() || !node.isObject()) {
            throw new IllegalArgumentException("message payload is required");
        }
        return objectMapper.treeToValue(node, type);
    }

    private ClientMessageType parseType(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("message type is required");
        try {
            return ClientMessageType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("unknown message type", error);
        }
    }

    private String requiredCommandId(String commandId) {
        String value = optionalCommandId(commandId);
        if (value == null) throw new IllegalArgumentException("command id is required");
        return value;
    }

    private String optionalCommandId(String commandId) {
        if (commandId == null) return null;
        String value = commandId.trim();
        if (value.isEmpty()) return null;
        if (value.length() > 128) throw new IllegalArgumentException("command id is too long");
        return value;
    }

    private void sendBoundError(SocketIdentity identity, String commandId, RuntimeException error) {
        send(identity, ServerEnvelope.error(
                identity.roomId(), commandId, errorCode(error), safeMessage(error)
        ));
    }

    private void send(SocketIdentity identity, ServerEnvelope envelope) {
        try {
            connections.sendCurrent(identity.connectionId(), objectMapper.writeValueAsString(envelope));
            log.debug("WS_MESSAGE_SENT roomId={} playerId={} connectionId={} type={} sequence={}",
                    identity.roomId(), identity.playerId(), identity.connectionId(),
                    envelope.type(), envelope.sequence());
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("server message serialization failed", error);
        }
    }

    private void sendRawErrorAndClose(
            WebSocketSession session,
            String roomId,
            String commandId,
            String code,
            String message
    ) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                        ServerEnvelope.error(roomId, commandId, code, message)
                )));
            }
        } catch (IOException ignored) {
            // 握手后立刻失败的连接可能已关闭。
        } finally {
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
        }
    }

    private String errorCode(RuntimeException error) {
        if (error instanceof IllegalActionException actionError) return actionError.code().name();
        if (error instanceof RoomApplicationException roomError) return roomError.code().name();
        return "INVALID_CONNECTION";
    }

    private String safeMessage(RuntimeException error) {
        if (error instanceof IllegalActionException || error instanceof RoomApplicationException) {
            return error.getMessage();
        }
        return "connection could not be established";
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        if (!session.isOpen()) return;
        try {
            session.close(status);
        } catch (IOException ignored) {
            // 已关闭，无需再次处理。
        }
    }
}
