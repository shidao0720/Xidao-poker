package com.xidao.poker.web.arena;

import com.xidao.poker.application.account.AccountPrincipal;
import com.xidao.poker.application.account.AccountProfile;
import com.xidao.poker.application.account.AccountService;
import com.xidao.poker.application.account.WalletSnapshot;
import com.xidao.poker.config.PokerPersistenceProperties;
import com.xidao.poker.web.api.SessionCookie;
import com.xidao.poker.web.protocol.ProtocolCompatibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArenaHandshakeInterceptorTest {
    @Test
    void authenticatedIdentitySupportsUnicodeGameIdAndIgnoresClientIdentityValidation() {
        AccountService accounts = mock(AccountService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<AccountService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(accounts);
        UUID accountId = UUID.randomUUID();
        when(accounts.authenticate("session-token")).thenReturn(new AccountPrincipal(accountId, "士道"));
        when(accounts.profile("session-token")).thenReturn(new AccountProfile(
                "士道", List.of("士道"), new WalletSnapshot(10_000, 0), "god"));
        ArenaHandshakeInterceptor interceptor = new ArenaHandshakeInterceptor(provider, enabledPersistence());
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create(versioned(
                "ws://localhost/ws/arena?roomId=arena_123456789abc&playerId=%E5%A3%AB%E9%81%93"
                        + "&playerName=%E5%A3%AB%E9%81%93&avatarKey=god")));
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, SessionCookie.NAME + "=session-token");
        when(request.getHeaders()).thenReturn(headers);
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(request, mock(ServerHttpResponse.class),
                mock(WebSocketHandler.class), attributes);

        assertThat(accepted).isTrue();
        ArenaHandshakeRequest identity = (ArenaHandshakeRequest) attributes.get(ArenaHandshakeInterceptor.ATTRIBUTE);
        assertThat(identity.playerId()).isEqualTo("士道");
        assertThat(identity.playerName()).isEqualTo("士道");
        assertThat(identity.avatarKey()).isEqualTo("god");
        assertThat(identity.accountId()).isEqualTo(accountId);
    }

    private static PokerPersistenceProperties enabledPersistence() {
        return new PokerPersistenceProperties(true, 1, 16, 1, Duration.ZERO, Duration.ZERO,
                new PokerPersistenceProperties.Database("jdbc:postgresql://localhost/test", "test", "test", 1));
    }

    private static String versioned(String uri) {
        return uri + "&protocolVersion=" + ProtocolCompatibility.CURRENT_PROTOCOL_VERSION
                + "&buildVersion=" + ProtocolCompatibility.currentBuildVersion();
    }
}
