package com.xidao.poker.web.ws;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import com.xidao.poker.web.protocol.ProtocolCompatibility;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PokerHandshakeInterceptorTest {
    private final PokerHandshakeInterceptor interceptor = new PokerHandshakeInterceptor();

    @Test
    void bindsNewPlayerIdentityFromHandshakeOnce() {
        ServerHttpRequest request = request(
                versioned("ws://localhost/ws/poker?roomId=room-1&playerId=alice_1&playerName=%E7%88%B1%E4%B8%BD%E4%B8%9D")
        );
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(
                request,
                mock(ServerHttpResponse.class),
                mock(WebSocketHandler.class),
                attributes
        );

        assertThat(accepted).isTrue();
        PokerHandshakeRequest identity = (PokerHandshakeRequest) attributes.get(
                PokerHandshakeInterceptor.HANDSHAKE_ATTRIBUTE
        );
        assertThat(identity.roomId()).isEqualTo("room-1");
        assertThat(identity.playerId()).isEqualTo("alice_1");
        assertThat(identity.playerName()).isEqualTo("爱丽丝");
        assertThat(identity.reconnecting()).isFalse();
    }

    @Test
    void acceptsReconnectOnlyWithEpochAndTokenTogether() {
        Map<String, Object> attributes = new HashMap<>();
        boolean accepted = interceptor.beforeHandshake(
                request(versioned("ws://localhost/ws/poker?roomId=room-1&playerId=alice&connectionEpoch=2&resumeToken=abc")),
                mock(ServerHttpResponse.class),
                mock(WebSocketHandler.class),
                attributes
        );

        assertThat(accepted).isTrue();
        PokerHandshakeRequest identity = (PokerHandshakeRequest) attributes.get(
                PokerHandshakeInterceptor.HANDSHAKE_ATTRIBUTE
        );
        assertThat(identity.expectedConnectionEpoch()).isEqualTo(2);
        assertThat(identity.resumeToken()).isEqualTo("abc");
    }

    @Test
    void rejectsPartialReconnectCredentialsBeforeUpgrade() {
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        boolean accepted = interceptor.beforeHandshake(
                request(versioned("ws://localhost/ws/poker?roomId=room-1&playerId=alice&connectionEpoch=2")),
                response,
                mock(WebSocketHandler.class),
                new HashMap<>()
        );

        assertThat(accepted).isFalse();
        verify(response).setStatusCode(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsHandshakeWithoutVersionMetadata() {
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        boolean accepted = interceptor.beforeHandshake(
                request("ws://localhost/ws/poker?roomId=room-1&playerId=alice&playerName=Alice"),
                response,
                mock(WebSocketHandler.class),
                new HashMap<>()
        );

        assertThat(accepted).isFalse();
        verify(response).setStatusCode(HttpStatus.BAD_REQUEST);
    }

    private String versioned(String uri) {
        return uri + "&protocolVersion=" + ProtocolCompatibility.CURRENT_PROTOCOL_VERSION
                + "&buildVersion=" + ProtocolCompatibility.currentBuildVersion();
    }

    private ServerHttpRequest request(String uri) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create(uri));
        return request;
    }
}
