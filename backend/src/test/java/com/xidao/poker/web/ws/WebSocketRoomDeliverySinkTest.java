package com.xidao.poker.web.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.xidao.poker.application.room.RoomDelivery;
import com.xidao.poker.engine.event.GameEvent;
import com.xidao.poker.engine.event.GameEventType;
import com.xidao.poker.engine.game.GamePhase;
import com.xidao.poker.engine.snapshot.ActionOptions;
import com.xidao.poker.engine.snapshot.GameSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketRoomDeliverySinkTest {
    @Test
    void broadcastsPublicEventsButTargetsSnapshotToOneExactConnection() throws Exception {
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        WebSocketConnectionRegistry registry = new WebSocketConnectionRegistry(10_000, 1_048_576);
        WebSocketSession alice = session("alice-socket");
        WebSocketSession bob = session("bob-socket");
        WebSocketConnectionRegistry.PreparedConnection aliceConnection = join(registry, alice, "A", "Alice");
        join(registry, bob, "B", "Bob");
        WebSocketRoomDeliverySink sink = new WebSocketRoomDeliverySink(registry, objectMapper);

        sink.deliver("room", new RoomDelivery.Events(List.of(new GameEvent(
                1,
                GameEventType.PLAYER_JOINED,
                0,
                "A",
                Map.of("seat", 0)
        ))));

        verify(alice).sendMessage(any(TextMessage.class));
        verify(bob).sendMessage(any(TextMessage.class));
        clearInvocations(alice, bob);

        sink.deliver("room", new RoomDelivery.Snapshot(
                "A",
                aliceConnection.identity().connectionId(),
                aliceConnection.identity().connectionEpoch(),
                snapshot(1)
        ));

        var captor = org.mockito.ArgumentCaptor.forClass(TextMessage.class);
        verify(alice).sendMessage(captor.capture());
        verify(bob, never()).sendMessage(any(TextMessage.class));
        JsonNode sent = objectMapper.readTree(captor.getValue().getPayload());
        assertThat(sent.path("type").asText()).isEqualTo("ROOM_SNAPSHOT");
        assertThat(sent.path("connectionEpoch").asLong()).isEqualTo(1);
        assertThat(sent.path("payload").path("resumeToken").asText()).isNotBlank();
    }

    @Test
    void silentlyDropsSnapshotQueuedForReplacedSocket() throws Exception {
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        WebSocketConnectionRegistry registry = new WebSocketConnectionRegistry(10_000, 1_048_576);
        WebSocketSession oldSession = session("old");
        WebSocketConnectionRegistry.PreparedConnection joined = join(registry, oldSession, "A", "Alice");
        WebSocketSession replacement = session("new");
        WebSocketConnectionRegistry.PreparedConnection reconnected = registry.prepare(
                replacement,
                new PokerHandshakeRequest(
                        "room", "A", null, 1L, joined.identity().resumeToken()
                )
        );
        registry.commit(reconnected);
        clearInvocations(oldSession, replacement);

        new WebSocketRoomDeliverySink(registry, objectMapper).deliver(
                "room",
                new RoomDelivery.Snapshot("A", "old", 1, snapshot(1))
        );

        verify(oldSession, never()).sendMessage(any(TextMessage.class));
        verify(replacement, never()).sendMessage(any(TextMessage.class));
    }

    private WebSocketConnectionRegistry.PreparedConnection join(
            WebSocketConnectionRegistry registry,
            WebSocketSession session,
            String playerId,
            String name
    ) {
        WebSocketConnectionRegistry.PreparedConnection prepared = registry.prepare(
                session,
                new PokerHandshakeRequest("room", playerId, name, null, null)
        );
        registry.commit(prepared);
        return prepared;
    }

    private GameSnapshot snapshot(long sequence) {
        return new GameSnapshot(
                "room", "A", 0, GamePhase.WAITING,
                null, null, null, null, 0,
                0, 10, 0, List.of(), List.of(), List.of(),
                ActionOptions.none(), List.of(), sequence
        );
    }

    private WebSocketSession session(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
