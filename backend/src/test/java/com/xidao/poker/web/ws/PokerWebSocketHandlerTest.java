package com.xidao.poker.web.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.xidao.poker.application.room.GameApplicationService;
import com.xidao.poker.application.room.RoomEventDispatcher;
import com.xidao.poker.application.room.RoomRegistry;
import com.xidao.poker.application.room.RoomService;
import com.xidao.poker.config.PokerNetworkProperties;
import com.xidao.poker.engine.game.GameConfig;
import com.xidao.poker.web.protocol.ProtocolCompatibility;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PokerWebSocketHandlerTest {
    @Test
    void rejectsIncompatibleProtocolBeforeJoiningRoom() throws Exception {
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        RoomRegistry rooms = new RoomRegistry();
        new RoomService(rooms, Clock.systemUTC()).createRoom(
                "room", "LAN", new GameConfig(5, 10, 1_000, 10)
        );
        WebSocketConnectionRegistry connections = new WebSocketConnectionRegistry(10_000, 1_048_576);
        PokerWebSocketHandler handler = new PokerWebSocketHandler(
                new GameApplicationService(
                        rooms,
                        new RoomEventDispatcher(
                                rooms,
                                new WebSocketRoomDeliverySink(connections, objectMapper),
                                Runnable::run
                        )
                ),
                connections,
                mock(DisconnectGraceScheduler.class),
                objectMapper,
                new PokerNetworkProperties(Duration.ofSeconds(30), Duration.ofSeconds(30), 16_384, 10_000, 1_048_576, List.of())
        );
        CapturedSession incompatible = capturedSession(
                "socket-old",
                new PokerHandshakeRequest(
                        "room", "A", "Alice", null, null,
                        ProtocolCompatibility.CURRENT_PROTOCOL_VERSION + 1,
                        ProtocolCompatibility.currentBuildVersion()
                )
        );

        handler.afterConnectionEstablished(incompatible.session());

        JsonNode error = lastMessageOfType(objectMapper, incompatible.messages(), "ERROR");
        assertThat(error.path("payload").path("code").asText()).isEqualTo("PROTOCOL_VERSION_MISMATCH");
        assertThat(connections.currentIdentity("socket-old")).isEmpty();
    }

    @Test
    void completeHandshakeAndStartHandKeepsHoleCardsViewerSpecific() throws Exception {
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        RoomRegistry rooms = new RoomRegistry();
        new RoomService(rooms, Clock.systemUTC()).createRoom(
                "room", "LAN", new GameConfig(5, 10, 1_000, 10)
        );
        WebSocketConnectionRegistry connections = new WebSocketConnectionRegistry(10_000, 1_048_576);
        WebSocketRoomDeliverySink sink = new WebSocketRoomDeliverySink(connections, objectMapper);
        RoomEventDispatcher dispatcher = new RoomEventDispatcher(rooms, sink, Runnable::run);
        GameApplicationService games = new GameApplicationService(rooms, dispatcher);
        DisconnectGraceScheduler disconnects = mock(DisconnectGraceScheduler.class);
        PokerWebSocketHandler handler = new PokerWebSocketHandler(
                games,
                connections,
                disconnects,
                objectMapper,
                new PokerNetworkProperties(Duration.ofSeconds(30), Duration.ofSeconds(30), 16_384, 10_000, 1_048_576, List.of())
        );

        CapturedSession alice = session("socket-A", "A", "Alice");
        CapturedSession bob = session("socket-B", "B", "Bob");
        handler.afterConnectionEstablished(alice.session());
        handler.afterConnectionEstablished(bob.session());

        JsonNode joined = lastMessageOfType(objectMapper, alice.messages(), "PLAYER_JOINED");
        assertThat(joined.path("payload").path("playerId").asText()).isEqualTo("B");
        JsonNode joinedData = joined.path("payload").path("data");
        assertThat(joinedData.path("name").asText()).isEqualTo("Bob");
        assertThat(joinedData.path("seat").asInt()).isEqualTo(1);
        assertThat(joinedData.path("stack").asInt()).isEqualTo(1_000);
        assertThat(joinedData.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(joinedData.path("inHand").asBoolean()).isFalse();
        assertThat(joinedData.path("canAct").asBoolean()).isFalse();
        assertThat(joinedData.path("ready").asBoolean()).isFalse();
        assertThat(joinedData.path("phase").asText()).isEqualTo("WAITING");

        handler.handleMessage(alice.session(), message("READY", "ready-A", "{\"ready\":true}"));
        handler.handleMessage(bob.session(), message("READY", "ready-B", "{\"ready\":true}"));
        handler.handleMessage(alice.session(), message(
                "START_GAME", "start-1", "{\"expectedPreviousHandId\":0}"
        ));

        JsonNode aliceSnapshot = lastMessageOfType(objectMapper, alice.messages(), "ROOM_SNAPSHOT");
        JsonNode bobSnapshot = lastMessageOfType(objectMapper, bob.messages(), "ROOM_SNAPSHOT");
        assertThat(holeCardCount(aliceSnapshot, "A")).isEqualTo(2);
        assertThat(holeCardCount(aliceSnapshot, "B")).isZero();
        assertThat(holeCardCount(bobSnapshot, "B")).isEqualTo(2);
        assertThat(holeCardCount(bobSnapshot, "A")).isZero();
        assertThat(messageTypes(objectMapper, alice.messages()))
                .contains("CONNECTION_READY", "PLAYER_JOINED", "READY_CHANGED", "HAND_STARTED", "COMMAND_RESULT");
    }

    @Test
    void invalidMessageReturnsStableErrorWithoutChangingBinding() throws Exception {
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        RoomRegistry rooms = new RoomRegistry();
        new RoomService(rooms, Clock.systemUTC()).createRoom(
                "room", "LAN", new GameConfig(5, 10, 1_000, 10)
        );
        WebSocketConnectionRegistry connections = new WebSocketConnectionRegistry(10_000, 1_048_576);
        RoomEventDispatcher dispatcher = new RoomEventDispatcher(
                rooms,
                new WebSocketRoomDeliverySink(connections, objectMapper),
                Runnable::run
        );
        PokerWebSocketHandler handler = new PokerWebSocketHandler(
                new GameApplicationService(rooms, dispatcher),
                connections,
                mock(DisconnectGraceScheduler.class),
                objectMapper,
                new PokerNetworkProperties(Duration.ofSeconds(30), Duration.ofSeconds(30), 16_384, 10_000, 1_048_576, List.of())
        );
        CapturedSession alice = session("socket-A", "A", "Alice");
        handler.afterConnectionEstablished(alice.session());

        handler.handleMessage(alice.session(), new TextMessage(
                "{\"type\":\"PLAYER_ACTION\",\"requestId\":\"bad\",\"payload\":{\"handId\":0}}"
        ));

        JsonNode error = lastMessageOfType(objectMapper, alice.messages(), "ERROR");
        assertThat(error.path("payload").path("code").asText()).isEqualTo("INVALID_MESSAGE");
        assertThat(connections.currentIdentity("socket-A"))
                .get()
                .extracting(SocketIdentity::playerId)
                .isEqualTo("A");
    }

    @Test
    void closedSocketCanReconnectWithRotatedTokenAndReceivesFreshSnapshot() throws Exception {
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        RoomRegistry rooms = new RoomRegistry();
        new RoomService(rooms, Clock.systemUTC()).createRoom(
                "room", "LAN", new GameConfig(5, 10, 1_000, 10)
        );
        WebSocketConnectionRegistry connections = new WebSocketConnectionRegistry(10_000, 1_048_576);
        GameApplicationService games = new GameApplicationService(
                rooms,
                new RoomEventDispatcher(
                        rooms,
                        new WebSocketRoomDeliverySink(connections, objectMapper),
                        Runnable::run
                )
        );
        DisconnectGraceScheduler disconnects = mock(DisconnectGraceScheduler.class);
        PokerWebSocketHandler handler = new PokerWebSocketHandler(
                games,
                connections,
                disconnects,
                objectMapper,
                new PokerNetworkProperties(Duration.ofSeconds(30), Duration.ofSeconds(30), 16_384, 10_000, 1_048_576, List.of())
        );
        CapturedSession original = session("socket-old", "A", "Alice");
        handler.afterConnectionEstablished(original.session());
        JsonNode firstReady = lastMessageOfType(objectMapper, original.messages(), "CONNECTION_READY");
        String oldToken = firstReady.path("payload").path("resumeToken").asText();
        clearInvocations(disconnects);

        handler.afterConnectionClosed(original.session(), CloseStatus.GOING_AWAY);
        verify(disconnects).schedule(any(SocketIdentity.class));

        CapturedSession replacement = reconnectSession("socket-new", "A", 1, oldToken);
        handler.afterConnectionEstablished(replacement.session());

        JsonNode ready = lastMessageOfType(objectMapper, replacement.messages(), "CONNECTION_READY");
        JsonNode snapshot = lastMessageOfType(objectMapper, replacement.messages(), "ROOM_SNAPSHOT");
        assertThat(ready.path("connectionEpoch").asLong()).isEqualTo(2);
        assertThat(ready.path("payload").path("resumeToken").asText())
                .isNotBlank()
                .isNotEqualTo(oldToken);
        assertThat(snapshot.path("connectionEpoch").asLong()).isEqualTo(2);
        verify(disconnects).cancel("room", "A");
    }

    private TextMessage message(String type, String commandId, String payload) {
        return new TextMessage("{\"type\":\"" + type + "\",\"requestId\":\"" + commandId
                + "\",\"payload\":" + payload + "}");
    }

    private CapturedSession session(String connectionId, String playerId, String playerName) throws Exception {
        return capturedSession(
                connectionId,
                new PokerHandshakeRequest("room", playerId, playerName, null, null)
        );
    }

    private CapturedSession reconnectSession(
            String connectionId,
            String playerId,
            long expectedEpoch,
            String resumeToken
    ) throws Exception {
        return capturedSession(
                connectionId,
                new PokerHandshakeRequest("room", playerId, null, expectedEpoch, resumeToken)
        );
    }

    private CapturedSession capturedSession(
            String connectionId,
            PokerHandshakeRequest handshake
    ) throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        List<String> messages = new ArrayList<>();
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(PokerHandshakeInterceptor.HANDSHAKE_ATTRIBUTE, handshake);
        when(session.getId()).thenReturn(connectionId);
        when(session.getAttributes()).thenReturn(attributes);
        when(session.isOpen()).thenReturn(true);
        doAnswer(invocation -> {
            messages.add(((TextMessage) invocation.getArgument(0)).getPayload());
            return null;
        }).when(session).sendMessage(any(TextMessage.class));
        return new CapturedSession(session, messages);
    }

    private JsonNode lastMessageOfType(ObjectMapper mapper, List<String> messages, String type) {
        JsonNode found = null;
        for (String message : messages) {
            try {
                JsonNode candidate = mapper.readTree(message);
                if (type.equals(candidate.path("type").asText())) found = candidate;
            } catch (Exception error) {
                throw new AssertionError(error);
            }
        }
        assertThat(found).as("message type %s", type).isNotNull();
        return found;
    }

    private int holeCardCount(JsonNode snapshotEnvelope, String playerId) {
        for (JsonNode player : snapshotEnvelope.path("payload").path("state").path("players")) {
            if (playerId.equals(player.path("id").asText())) return player.path("holeCards").size();
        }
        throw new AssertionError("player not found: " + playerId);
    }

    private List<String> messageTypes(ObjectMapper mapper, List<String> messages) {
        List<String> types = new ArrayList<>();
        for (String message : messages) {
            try {
                types.add(mapper.readTree(message).path("type").asText());
            } catch (Exception error) {
                throw new AssertionError(error);
            }
        }
        return types;
    }

    private record CapturedSession(WebSocketSession session, List<String> messages) {
    }
}
