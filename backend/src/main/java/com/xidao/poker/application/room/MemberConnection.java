package com.xidao.poker.application.room;

/** RoomRuntime 保存连接标识和代次，但不保存 Spring WebSocketSession。 */
public record MemberConnection(String connectionId, long epoch, ConnectionState state) {
    public MemberConnection {
        if (connectionId == null || connectionId.isBlank()) {
            throw new IllegalArgumentException("connection id is required");
        }
        if (epoch <= 0) throw new IllegalArgumentException("connection epoch must be positive");
        if (state == null) throw new IllegalArgumentException("connection state is required");
    }

    public boolean matches(String candidateConnectionId, long candidateEpoch) {
        return connectionId.equals(candidateConnectionId) && epoch == candidateEpoch;
    }

    public MemberConnection reconnecting() {
        return new MemberConnection(connectionId, epoch, ConnectionState.RECONNECTING);
    }

    public MemberConnection reconnect(String nextConnectionId) {
        return new MemberConnection(nextConnectionId, Math.incrementExact(epoch), ConnectionState.CONNECTED);
    }

    public MemberConnection close() {
        return new MemberConnection(connectionId, epoch, ConnectionState.CLOSED);
    }
}
