package com.xidao.poker.web.ws;

import com.xidao.poker.application.account.AccountPrincipal;
import com.xidao.poker.application.account.AccountProfile;
import com.xidao.poker.application.account.AccountService;
import com.xidao.poker.application.account.AccountException;
import com.xidao.poker.application.account.AccountErrorCode;
import com.xidao.poker.application.account.AvatarCatalog;
import com.xidao.poker.config.PokerPersistenceProperties;
import com.xidao.poker.web.api.SessionCookie;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class PokerHandshakeInterceptor implements HandshakeInterceptor {
    static final String HANDSHAKE_ATTRIBUTE = PokerHandshakeRequest.class.getName();
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private final AccountService accounts;
    private final boolean authenticationRequired;

    /** Unit-test and guest-LAN constructor. */
    public PokerHandshakeInterceptor() {
        this.accounts = null;
        this.authenticationRequired = false;
    }

    @Autowired
    public PokerHandshakeInterceptor(
            ObjectProvider<AccountService> accountProvider,
            PokerPersistenceProperties persistence
    ) {
        this.accounts = accountProvider.getIfAvailable();
        this.authenticationRequired = persistence.enabled();
        if (authenticationRequired && accounts == null) {
            throw new IllegalStateException("account service is required when persistence is enabled");
        }
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        try {
            MultiValueMap<String, String> query = UriComponentsBuilder
                    .fromUri(request.getURI())
                    .build()
                    .getQueryParams();
            String roomId = requiredSafeId(query, "roomId");
            String requestedPlayerId = optionalTrimmed(query, "playerId");
            String playerId;
            String playerName = optionalTrimmed(query, "playerName");
            String avatarKey = AvatarCatalog.normalize(optionalTrimmed(query, "avatarKey"));
            Map<String, String> cosmetics = Map.of();
            String epochText = optionalTrimmed(query, "connectionEpoch");
            String resumeToken = optionalTrimmed(query, "resumeToken");
            int protocolVersion = requiredPositiveInteger(query, "protocolVersion");
            String buildVersion = requiredBuildVersion(query);
            AccountPrincipal principal = null;

            if (authenticationRequired) {
                String sessionToken = requiredSessionCookie(request);
                principal = accounts.authenticate(sessionToken);
                AccountProfile profile = accounts.profile(sessionToken);
                avatarKey = profile.avatarKey();
                cosmetics = profile.loadout().publicAppearance();
                playerId = principal.gameId();
                playerName = principal.gameId();
            } else {
                if (requestedPlayerId == null || !SAFE_ID.matcher(requestedPlayerId).matches()) {
                    throw new IllegalArgumentException("playerId is invalid");
                }
                playerId = requestedPlayerId;
            }

            Long epoch = epochText == null ? null : parsePositiveEpoch(epochText);
            boolean hasToken = resumeToken != null;
            if ((epoch == null) != !hasToken) {
                throw new IllegalArgumentException("connectionEpoch and resumeToken must be provided together");
            }
            if (epoch == null && (playerName == null || playerName.length() > 32)) {
                throw new IllegalArgumentException("playerName must be 1..32 characters for a new connection");
            }
            if (resumeToken != null && resumeToken.length() > 128) {
                throw new IllegalArgumentException("resume token is invalid");
            }

            attributes.put(
                    HANDSHAKE_ATTRIBUTE,
                    new PokerHandshakeRequest(
                            roomId,
                            playerId,
                            playerName,
                            avatarKey,
                            cosmetics,
                            epoch,
                            resumeToken,
                            protocolVersion,
                            buildVersion,
                            principal == null ? null : principal.accountId()
                    )
            );
            return true;
        } catch (AccountException error) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        } catch (IllegalArgumentException error) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }
    }

    private String requiredSessionCookie(ServerHttpRequest request) {
        for (String header : request.getHeaders().getOrEmpty(HttpHeaders.COOKIE)) {
            for (String part : header.split(";")) {
                int separator = part.indexOf('=');
                if (separator <= 0) continue;
                if (SessionCookie.NAME.equals(part.substring(0, separator).trim())) {
                    String value = part.substring(separator + 1).trim();
                    if (!value.isBlank()) return value;
                }
            }
        }
        throw new AccountException(AccountErrorCode.UNAUTHORIZED, "authenticated session is required");
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
        // no-op
    }

    private String requiredSafeId(MultiValueMap<String, String> query, String name) {
        String value = optionalTrimmed(query, name);
        if (value == null || !SAFE_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private String optionalTrimmed(MultiValueMap<String, String> query, String name) {
        if (query.get(name) != null && query.get(name).size() != 1) {
            throw new IllegalArgumentException(name + " must occur once");
        }
        String value = query.getFirst(name);
        if (value == null) return null;
        String trimmed = UriUtils.decode(value, StandardCharsets.UTF_8).trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private long parsePositiveEpoch(String value) {
        try {
            long epoch = Long.parseLong(value);
            if (epoch <= 0) throw new IllegalArgumentException("connection epoch must be positive");
            return epoch;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("connection epoch is invalid", error);
        }
    }

    private int requiredPositiveInteger(MultiValueMap<String, String> query, String name) {
        String value = optionalTrimmed(query, name);
        if (value == null) throw new IllegalArgumentException(name + " is required");
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) throw new IllegalArgumentException(name + " must be positive");
            return parsed;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(name + " is invalid", error);
        }
    }

    private String requiredBuildVersion(MultiValueMap<String, String> query) {
        String value = optionalTrimmed(query, "buildVersion");
        if (value == null || value.length() > 64 || !value.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("buildVersion is invalid");
        }
        return value;
    }
}
