package com.xidao.poker.web.ws;

/** resumeToken 不参与 toString，避免日志意外泄露重连凭据。 */
public final class SocketIdentity {
    private final String roomId;
    private final String playerId;
    private final String playerName;
    private final String connectionId;
    private final long connectionEpoch;
    private final String resumeToken;

    SocketIdentity(
            String roomId,
            String playerId,
            String playerName,
            String connectionId,
            long connectionEpoch,
            String resumeToken
    ) {
        this.roomId = roomId;
        this.playerId = playerId;
        this.playerName = playerName;
        this.connectionId = connectionId;
        this.connectionEpoch = connectionEpoch;
        this.resumeToken = resumeToken;
    }

    public String roomId() { return roomId; }

    public String playerId() { return playerId; }

    public String playerName() { return playerName; }

    public String connectionId() { return connectionId; }

    public long connectionEpoch() { return connectionEpoch; }

    public String resumeToken() { return resumeToken; }

    @Override
    public String toString() {
        return "SocketIdentity[roomId=" + roomId
                + ", playerId=" + playerId
                + ", connectionId=" + connectionId
                + ", connectionEpoch=" + connectionEpoch + "]";
    }
}
