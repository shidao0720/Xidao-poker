package com.xidao.poker.web.arena;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ArenaConnectionRegistry {
    private final SecureRandom random = new SecureRandom();
    private final Map<String, ArenaSocketIdentity> bySession = new HashMap<>();
    private final Map<String, Member> members = new HashMap<>();
    private final int sendTimeLimitMillis;
    private final int sendBufferBytes;

    public ArenaConnectionRegistry(int sendTimeLimitMillis, int sendBufferBytes) {
        if (sendTimeLimitMillis <= 0 || sendBufferBytes <= 0) {
            throw new IllegalArgumentException("arena websocket limits must be positive");
        }
        this.sendTimeLimitMillis = sendTimeLimitMillis;
        this.sendBufferBytes = sendBufferBytes;
    }

    public synchronized ArenaSocketIdentity prepare(WebSocketSession session, ArenaHandshakeRequest request) {
        String key = key(request.roomId(), request.playerId());
        Member previous = members.get(key);
        if (previous != null && !sameAccount(previous.identity.accountId(), request.accountId())) {
            if (request.resumeToken() == null || !request.resumeToken().equals(previous.identity.resumeToken())) {
                throw new IllegalStateException("arena reconnect token is invalid");
            }
        }
        if (previous != null) {
            bySession.remove(previous.session.getId());
            closeQuietly(previous.session, new CloseStatus(4001, "connection replaced"));
        }
        long epoch = previous == null ? 1 : previous.identity.connectionEpoch() + 1;
        ArenaSocketIdentity identity = new ArenaSocketIdentity(session.getId(), request.roomId(),
                request.playerId(), request.playerName(), request.avatarKey(), epoch, token(), request.accountId());
        ConcurrentWebSocketSessionDecorator safeSession = new ConcurrentWebSocketSessionDecorator(
                session, sendTimeLimitMillis, sendBufferBytes);
        members.put(key, new Member(identity, safeSession));
        bySession.put(session.getId(), identity);
        return identity;
    }

    public synchronized Optional<ArenaSocketIdentity> detach(String sessionId) {
        ArenaSocketIdentity identity = bySession.remove(sessionId);
        if (identity == null) return Optional.empty();
        Member current = members.get(key(identity.roomId(), identity.playerId()));
        if (current == null || current.identity.connectionEpoch() != identity.connectionEpoch()) return Optional.empty();
        return Optional.of(identity);
    }

    public synchronized Optional<ArenaSocketIdentity> identity(String sessionId) {
        return Optional.ofNullable(bySession.get(sessionId));
    }

    public synchronized void forgetIfCurrent(String roomId, String playerId, long epoch) {
        String key = key(roomId, playerId);
        Member current = members.get(key);
        if (current == null || current.identity.connectionEpoch() != epoch) return;
        members.remove(key);
        bySession.remove(current.session.getId());
    }

    public void send(ArenaSocketIdentity identity, String json) throws IOException {
        ConcurrentWebSocketSessionDecorator session;
        synchronized (this) {
            Member current = members.get(key(identity.roomId(), identity.playerId()));
            if (current == null || current.identity.connectionEpoch() != identity.connectionEpoch()) return;
            session = current.session;
        }
        send(session, json);
    }

    public void broadcast(String roomId, String json) {
        ArrayList<ConcurrentWebSocketSessionDecorator> sessions = new ArrayList<>();
        synchronized (this) {
            members.values().stream().filter(member -> member.identity.roomId().equals(roomId))
                    .forEach(member -> sessions.add(member.session));
        }
        for (ConcurrentWebSocketSessionDecorator session : sessions) {
            try {
                send(session, json);
            } catch (IOException | RuntimeException error) {
                closeQuietly(session, CloseStatus.SERVER_ERROR);
            }
        }
    }

    private static void send(WebSocketSession session, String json) throws IOException {
        if (session.isOpen()) session.sendMessage(new TextMessage(json));
    }

    private static boolean sameAccount(UUID left, UUID right) {
        return left != null && left.equals(right);
    }

    private String token() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String key(String roomId, String playerId) { return roomId + '\u0000' + playerId; }

    private static void closeQuietly(WebSocketSession session, CloseStatus status) {
        try { if (session.isOpen()) session.close(status); } catch (IOException ignored) { }
    }

    private record Member(ArenaSocketIdentity identity, ConcurrentWebSocketSessionDecorator session) { }
}
