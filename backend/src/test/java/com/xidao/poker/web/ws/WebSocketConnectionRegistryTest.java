package com.xidao.poker.web.ws;

import com.xidao.poker.application.room.RoomApplicationException;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketConnectionRegistryTest {
    @Test
    void reconnectRotatesTokenAndMakesOldSocketStale() throws Exception {
        WebSocketConnectionRegistry registry = new WebSocketConnectionRegistry(10_000, 1_048_576);
        WebSocketSession oldSession = session("old");
        WebSocketConnectionRegistry.PreparedConnection joined = registry.prepare(
                oldSession,
                new PokerHandshakeRequest("room", "A", "Alice", null, null)
        );
        registry.commit(joined);
        String oldToken = joined.identity().resumeToken();

        WebSocketSession newSession = session("new");
        WebSocketConnectionRegistry.PreparedConnection reconnected = registry.prepare(
                newSession,
                new PokerHandshakeRequest("room", "A", null, 1L, oldToken)
        );
        registry.commit(reconnected);

        assertThat(reconnected.identity().connectionEpoch()).isEqualTo(2);
        assertThat(reconnected.identity().resumeToken()).isNotEqualTo(oldToken);
        assertThat(registry.currentIdentity("old")).isEmpty();
        assertThat(registry.currentIdentity("new")).contains(reconnected.identity());
        verify(oldSession).close(any());

        assertThat(registry.sendExact("room", "A", "old", 1, "old"))
                .isFalse();
        assertThat(registry.sendExact("room", "A", "new", 2, "new"))
                .isTrue();
        verify(oldSession, never()).sendMessage(any(TextMessage.class));
        verify(newSession).sendMessage(any(TextMessage.class));
    }

    @Test
    void rejectsWrongReconnectTokenWithoutReplacingCurrentSocket() {
        WebSocketConnectionRegistry registry = new WebSocketConnectionRegistry(10_000, 1_048_576);
        WebSocketSession current = session("current");
        WebSocketConnectionRegistry.PreparedConnection joined = registry.prepare(
                current,
                new PokerHandshakeRequest("room", "A", "Alice", null, null)
        );
        registry.commit(joined);

        assertThatThrownBy(() -> registry.prepare(
                session("attacker"),
                new PokerHandshakeRequest("room", "A", null, 1L, "wrong-token")
        )).isInstanceOf(RoomApplicationException.class);

        assertThat(registry.currentIdentity("current")).contains(joined.identity());
        assertThat(registry.activeConnectionCount()).isEqualTo(1);
    }

    private WebSocketSession session(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
