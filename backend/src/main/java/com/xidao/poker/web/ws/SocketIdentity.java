package com.xidao.poker.web.ws;

import java.util.UUID;

/** resumeToken 不参与 toString，避免日志意外泄露重连凭据。 */
public final class SocketIdentity {
    private final String roomId;
    private final String playerId;
    private final String playerName;
    private final String avatarKey;
    private final String connectionId;
    private final long connectionEpoch;
    private final String resumeToken;
    private final UUID accountId;

    SocketIdentity(
            String roomId,
            String playerId,
            String playerName,
            String avatarKey,
            String connectionId,
            long connectionEpoch,
            String resumeToken,
            UUID accountId
    ) {
        this.roomId = roomId;
        this.playerId = playerId;
        this.playerName = playerName;
        this.avatarKey = avatarKey == null || avatarKey.isBlank() ? "default" : avatarKey;
        this.connectionId = connectionId;
        this.connectionEpoch = connectionEpoch;
        this.resumeToken = resumeToken;
        this.accountId = accountId;
    }

    SocketIdentity(
            String roomId, String playerId, String playerName, String connectionId,
            long connectionEpoch, String resumeToken
    ) {
        this(roomId, playerId, playerName, "default", connectionId, connectionEpoch, resumeToken, null);
    }

    public String roomId() { return roomId; }

    public String playerId() { return playerId; }

    public String playerName() { return playerName; }

    public String avatarKey() { return avatarKey; }

    public String connectionId() { return connectionId; }

    public long connectionEpoch() { return connectionEpoch; }

    public String resumeToken() { return resumeToken; }

    public UUID accountId() { return accountId; }

    @Override
    public String toString() {
        return "SocketIdentity[roomId=" + roomId
                + ", playerId=" + playerId
                + ", connectionId=" + connectionId
                + ", connectionEpoch=" + connectionEpoch + "]";
    }
}
