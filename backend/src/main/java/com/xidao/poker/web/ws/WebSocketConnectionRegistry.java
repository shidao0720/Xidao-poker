package com.xidao.poker.web.ws;

import com.xidao.poker.application.room.RoomApplicationErrorCode;
import com.xidao.poker.application.room.RoomApplicationException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 网络适配器自己的连接目录。RoomRuntime 只看 connectionId/epoch，不保存 WebSocketSession 或重连令牌。
 */
public final class WebSocketConnectionRegistry {
    private static final CloseStatus REPLACED = new CloseStatus(4001, "connection replaced");

    private final ReentrantLock lock = new ReentrantLock(true);
    private final Map<String, ManagedConnection> connections = new LinkedHashMap<>();
    private final Map<MemberKey, MemberTicket> tickets = new LinkedHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    private final int sendTimeLimitMillis;
    private final int sendBufferBytes;

    public WebSocketConnectionRegistry(int sendTimeLimitMillis, int sendBufferBytes) {
        if (sendTimeLimitMillis <= 0 || sendBufferBytes <= 0) {
            throw new IllegalArgumentException("websocket send limits must be positive");
        }
        this.sendTimeLimitMillis = sendTimeLimitMillis;
        this.sendBufferBytes = sendBufferBytes;
    }

    PreparedConnection prepare(WebSocketSession session, PokerHandshakeRequest request) {
        if (session == null || request == null) throw new IllegalArgumentException("session and handshake are required");
        lock.lock();
        try {
            if (connections.containsKey(session.getId())) {
                throw new IllegalStateException("connection id is already registered");
            }
            MemberKey key = new MemberKey(request.roomId(), request.playerId());
            MemberTicket previous = tickets.get(key);
            SocketIdentity identity;
            if (request.reconnecting()) {
                if (previous == null) throw memberNotConnected();
                if (previous.epoch() != request.expectedConnectionEpoch()
                        || !constantTimeEquals(previous.resumeToken(), request.resumeToken())) {
                    throw staleConnection();
                }
                identity = new SocketIdentity(
                        request.roomId(),
                        request.playerId(),
                        previous.playerName(),
                        session.getId(),
                        Math.incrementExact(previous.epoch()),
                        nextToken()
                );
            } else {
                if (previous != null) {
                    throw new RoomApplicationException(
                            RoomApplicationErrorCode.MEMBER_ALREADY_CONNECTED,
                            "player already has a room connection"
                    );
                }
                identity = new SocketIdentity(
                        request.roomId(),
                        request.playerId(),
                        request.playerName(),
                        session.getId(),
                        1,
                        nextToken()
                );
            }

            ConcurrentWebSocketSessionDecorator safeSession = new ConcurrentWebSocketSessionDecorator(
                    session,
                    sendTimeLimitMillis,
                    sendBufferBytes
            );
            ManagedConnection managed = new ManagedConnection(identity, safeSession);
            connections.put(identity.connectionId(), managed);
            tickets.put(key, MemberTicket.from(identity));
            return new PreparedConnection(identity, previous);
        } finally {
            lock.unlock();
        }
    }

    void commit(PreparedConnection prepared) {
        ManagedConnection replaced = null;
        lock.lock();
        try {
            requirePreparedCurrent(prepared);
            if (prepared.previous() != null
                    && !prepared.previous().connectionId().equals(prepared.identity().connectionId())) {
                replaced = connections.remove(prepared.previous().connectionId());
            }
        } finally {
            lock.unlock();
        }
        closeQuietly(replaced, REPLACED);
    }

    void rollback(PreparedConnection prepared) {
        ManagedConnection current;
        lock.lock();
        try {
            MemberKey key = MemberKey.from(prepared.identity());
            MemberTicket ticket = tickets.get(key);
            if (ticket != null && ticket.matches(prepared.identity())) {
                if (prepared.previous() == null) tickets.remove(key);
                else tickets.put(key, prepared.previous());
            }
            current = connections.remove(prepared.identity().connectionId());
        } finally {
            lock.unlock();
        }
        closeQuietly(current, CloseStatus.SERVER_ERROR);
    }

    Optional<SocketIdentity> currentIdentity(String connectionId) {
        lock.lock();
        try {
            ManagedConnection connection = connections.get(connectionId);
            if (connection == null || !isCurrent(connection.identity())) return Optional.empty();
            return Optional.of(connection.identity());
        } finally {
            lock.unlock();
        }
    }

    Optional<SocketIdentity> detach(String connectionId) {
        lock.lock();
        try {
            ManagedConnection removed = connections.remove(connectionId);
            return removed == null ? Optional.empty() : Optional.of(removed.identity());
        } finally {
            lock.unlock();
        }
    }

    void forgetMember(String roomId, String playerId, long expectedEpoch) {
        lock.lock();
        try {
            MemberKey key = new MemberKey(roomId, playerId);
            MemberTicket ticket = tickets.get(key);
            if (ticket == null || ticket.epoch() != expectedEpoch) return;
            tickets.remove(key);
            connections.remove(ticket.connectionId());
        } finally {
            lock.unlock();
        }
    }

    boolean sendExact(
            String roomId,
            String playerId,
            String connectionId,
            long connectionEpoch,
            String json
    ) {
        ManagedConnection target;
        lock.lock();
        try {
            target = connections.get(connectionId);
            if (target == null
                    || !target.identity().roomId().equals(roomId)
                    || !target.identity().playerId().equals(playerId)
                    || target.identity().connectionEpoch() != connectionEpoch
                    || !isCurrent(target.identity())) {
                return false;
            }
        } finally {
            lock.unlock();
        }
        send(target, json);
        return true;
    }

    void sendCurrent(String connectionId, String json) {
        ManagedConnection target;
        lock.lock();
        try {
            target = connections.get(connectionId);
            if (target == null || !isCurrent(target.identity())) throw staleConnection();
        } finally {
            lock.unlock();
        }
        send(target, json);
    }

    void broadcast(String roomId, String json) {
        List<ManagedConnection> targets = new ArrayList<>();
        lock.lock();
        try {
            for (ManagedConnection connection : connections.values()) {
                if (connection.identity().roomId().equals(roomId) && isCurrent(connection.identity())) {
                    targets.add(connection);
                }
            }
        } finally {
            lock.unlock();
        }

        RuntimeException firstFailure = null;
        for (ManagedConnection target : targets) {
            try {
                send(target, json);
            } catch (RuntimeException error) {
                if (firstFailure == null) firstFailure = error;
            }
        }
        if (firstFailure != null) throw firstFailure;
    }

    Optional<String> resumeToken(
            String roomId,
            String playerId,
            String connectionId,
            long connectionEpoch
    ) {
        lock.lock();
        try {
            MemberTicket ticket = tickets.get(new MemberKey(roomId, playerId));
            return ticket != null
                    && ticket.connectionId().equals(connectionId)
                    && ticket.epoch() == connectionEpoch
                    ? Optional.of(ticket.resumeToken())
                    : Optional.empty();
        } finally {
            lock.unlock();
        }
    }

    int activeConnectionCount() {
        lock.lock();
        try {
            return (int) connections.values().stream()
                    .filter(connection -> isCurrent(connection.identity()))
                    .count();
        } finally {
            lock.unlock();
        }
    }

    private void requirePreparedCurrent(PreparedConnection prepared) {
        MemberTicket ticket = tickets.get(MemberKey.from(prepared.identity()));
        if (ticket == null || !ticket.matches(prepared.identity())) throw staleConnection();
    }

    private boolean isCurrent(SocketIdentity identity) {
        MemberTicket ticket = tickets.get(MemberKey.from(identity));
        return ticket != null && ticket.matches(identity);
    }

    private void send(ManagedConnection target, String json) {
        if (!target.session().isOpen()) {
            throw new IllegalStateException("websocket connection is closed");
        }
        try {
            target.session().sendMessage(new TextMessage(json));
        } catch (IOException error) {
            throw new IllegalStateException("websocket send failed", error);
        }
    }

    private String nextToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) return false;
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8)
        );
    }

    private RoomApplicationException staleConnection() {
        return new RoomApplicationException(
                RoomApplicationErrorCode.STALE_CONNECTION,
                "connection identity is stale"
        );
    }

    private RoomApplicationException memberNotConnected() {
        return new RoomApplicationException(
                RoomApplicationErrorCode.MEMBER_NOT_CONNECTED,
                "no reconnectable player connection exists"
        );
    }

    private void closeQuietly(ManagedConnection connection, CloseStatus status) {
        if (connection == null || !connection.session().isOpen()) return;
        try {
            connection.session().close(status);
        } catch (IOException ignored) {
            // 连接已失效，无额外恢复动作。
        }
    }

    record PreparedConnection(SocketIdentity identity, MemberTicket previous) {
    }

    private record ManagedConnection(
            SocketIdentity identity,
            ConcurrentWebSocketSessionDecorator session
    ) {
    }

    record MemberTicket(
            String playerName,
            String connectionId,
            long epoch,
            String resumeToken
    ) {
        static MemberTicket from(SocketIdentity identity) {
            return new MemberTicket(
                    identity.playerName(),
                    identity.connectionId(),
                    identity.connectionEpoch(),
                    identity.resumeToken()
            );
        }

        boolean matches(SocketIdentity identity) {
            return connectionId.equals(identity.connectionId())
                    && epoch == identity.connectionEpoch()
                    && resumeToken.equals(identity.resumeToken());
        }
    }

    private record MemberKey(String roomId, String playerId) {
        static MemberKey from(SocketIdentity identity) {
            return new MemberKey(identity.roomId(), identity.playerId());
        }
    }
}
