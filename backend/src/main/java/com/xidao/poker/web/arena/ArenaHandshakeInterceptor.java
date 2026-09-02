package com.xidao.poker.web.arena;

import com.xidao.poker.application.account.AccountException;
import com.xidao.poker.application.account.AccountErrorCode;
import com.xidao.poker.application.account.AccountPrincipal;
import com.xidao.poker.application.account.AccountProfile;
import com.xidao.poker.application.account.AccountService;
import com.xidao.poker.application.account.AvatarCatalog;
import com.xidao.poker.config.PokerPersistenceProperties;
import com.xidao.poker.web.api.SessionCookie;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;

public final class ArenaHandshakeInterceptor implements HandshakeInterceptor {
    static final String ATTRIBUTE = ArenaHandshakeRequest.class.getName();
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private final AccountService accounts;
    private final boolean authenticationRequired;

    public ArenaHandshakeInterceptor(ObjectProvider<AccountService> accounts,
                                     PokerPersistenceProperties persistence) {
        this.accounts = accounts.getIfAvailable();
        this.authenticationRequired = persistence.enabled();
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        try {
            MultiValueMap<String, String> query = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
            String roomId = safe(query, "roomId", true);
            String playerId;
            String playerName;
            String avatarKey;
            String resumeToken = text(query, "resumeToken");
            int protocolVersion = positiveInteger(query, "protocolVersion");
            String buildVersion = buildVersion(query);
            AccountPrincipal principal = null;
            if (authenticationRequired) {
                if (accounts == null) throw new IllegalStateException("account service is unavailable");
                String token = sessionCookie(request);
                principal = accounts.authenticate(token);
                AccountProfile profile = accounts.profile(token);
                playerId = profile.gameId();
                playerName = profile.gameId();
                avatarKey = profile.avatarKey();
            } else {
                playerId = safe(query, "playerId", false);
                playerName = text(query, "playerName");
                avatarKey = AvatarCatalog.normalize(text(query, "avatarKey"));
                if (playerId == null || playerName == null || playerName.length() > 32) {
                    throw new IllegalArgumentException("guest arena identity is invalid");
                }
            }
            if (resumeToken != null && (resumeToken.length() < 16 || resumeToken.length() > 128)) {
                throw new IllegalArgumentException("resume token is invalid");
            }
            attributes.put(ATTRIBUTE, new ArenaHandshakeRequest(roomId, playerId, playerName,
                    avatarKey, resumeToken, protocolVersion, buildVersion,
                    principal == null ? null : principal.accountId()));
            return true;
        } catch (AccountException error) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        } catch (RuntimeException error) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) { }

    private static String safe(MultiValueMap<String, String> query, String name, boolean required) {
        String value = text(query, name);
        if (value == null && !required) return null;
        if (value == null || !SAFE_ID.matcher(value).matches()) throw new IllegalArgumentException(name + " is invalid");
        return value;
    }

    private static String text(MultiValueMap<String, String> query, String name) {
        if (query.get(name) != null && query.get(name).size() != 1) throw new IllegalArgumentException(name + " must occur once");
        String value = query.getFirst(name);
        if (value == null) return null;
        String clean = UriUtils.decode(value, StandardCharsets.UTF_8).strip();
        return clean.isEmpty() ? null : clean;
    }

    private static int positiveInteger(MultiValueMap<String, String> query, String name) {
        try {
            int value = Integer.parseInt(text(query, name));
            if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
            return value;
        } catch (NumberFormatException | NullPointerException error) {
            throw new IllegalArgumentException(name + " is invalid", error);
        }
    }

    private static String buildVersion(MultiValueMap<String, String> query) {
        String value = text(query, "buildVersion");
        if (value == null || value.length() > 64 || !value.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("build version is invalid");
        }
        return value;
    }

    private static String sessionCookie(ServerHttpRequest request) {
        for (String header : request.getHeaders().getOrEmpty(HttpHeaders.COOKIE)) {
            for (String part : header.split(";")) {
                int separator = part.indexOf('=');
                if (separator > 0 && SessionCookie.NAME.equals(part.substring(0, separator).strip())) {
                    String value = part.substring(separator + 1).strip();
                    if (!value.isEmpty()) return value;
                }
            }
        }
        throw new AccountException(AccountErrorCode.UNAUTHORIZED, "authenticated session is required");
    }
}
