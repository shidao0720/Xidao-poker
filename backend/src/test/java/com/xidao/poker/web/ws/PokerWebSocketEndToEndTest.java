package com.xidao.poker.web.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xidao.poker.application.room.RoomService;
import com.xidao.poker.engine.game.GameConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import com.xidao.poker.web.protocol.ProtocolCompatibility;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PokerWebSocketEndToEndTest {
    @LocalServerPort
    private int port;

    @Autowired
    private RoomService rooms;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void realTomcatUpgradeFlowsThroughHandshakeApplicationAndOutbox() throws Exception {
        String roomId = "e2e-" + UUID.randomUUID();
        rooms.createRoom(roomId, "E2E", new GameConfig(5, 10, 1_000, 10));
        BlockingQueue<JsonNode> received = new LinkedBlockingQueue<>();
        WebSocketHandler clientHandler = new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
                received.add(objectMapper.readTree(message.getPayload()));
            }
        };
        ThreadPoolTaskExecutor clientExecutor = new ThreadPoolTaskExecutor();
        clientExecutor.setCorePoolSize(1);
        clientExecutor.setMaxPoolSize(1);
        clientExecutor.setThreadNamePrefix("poker-e2e-client-");
        clientExecutor.initialize();
        StandardWebSocketClient client = new StandardWebSocketClient();
        client.setTaskExecutor(clientExecutor);

        WebSocketSession session = null;
        try {
            URI uri = URI.create("ws://localhost:" + port + "/ws/poker?roomId=" + roomId
                    + "&playerId=E2E_A&playerName=Alice"
                    + "&protocolVersion=" + ProtocolCompatibility.CURRENT_PROTOCOL_VERSION
                    + "&buildVersion=" + ProtocolCompatibility.currentBuildVersion());
            session = client.execute(clientHandler, new WebSocketHttpHeaders(), uri)
                    .get(5, TimeUnit.SECONDS);

            JsonNode snapshot = awaitType(received, "ROOM_SNAPSHOT", Duration.ofSeconds(5));
            assertThat(snapshot.path("payload").path("state").path("players")).hasSize(1);
            assertThat(snapshot.path("payload").path("resumeToken").asText()).isNotBlank();

            session.sendMessage(new TextMessage("""
                    {"type":"READY","requestId":"e2e-ready","payload":{"ready":true}}
                    """));
            JsonNode readyEvent = awaitType(received, "READY_CHANGED", Duration.ofSeconds(5));
            assertThat(readyEvent.path("payload").path("playerId").asText()).isEqualTo("E2E_A");
            assertThat(readyEvent.path("payload").path("data").path("ready").asBoolean()).isTrue();
        } finally {
            if (session != null && session.isOpen()) session.close(CloseStatus.NORMAL);
            clientExecutor.shutdown();
        }
    }

    private JsonNode awaitType(
            BlockingQueue<JsonNode> messages,
            String expectedType,
            Duration timeout
    ) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            long remaining = Math.max(1, deadline - System.nanoTime());
            JsonNode message = messages.poll(remaining, TimeUnit.NANOSECONDS);
            if (message == null) break;
            if (expectedType.equals(message.path("type").asText())) return message;
        }
        throw new AssertionError("timed out waiting for websocket message type " + expectedType);
    }
}
