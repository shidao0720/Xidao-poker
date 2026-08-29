package com.xidao.poker.web.ws;

import com.xidao.poker.web.protocol.ProtocolCompatibility;

import java.util.UUID;

/** 握手完成后写入 WebSocket attributes，消息处理阶段不再读取可伪造的身份字段。 */
record PokerHandshakeRequest(
        String roomId,
        String playerId,
        String playerName,
        String avatarKey,
        Long expectedConnectionEpoch,
        String resumeToken,
        int protocolVersion,
        String buildVersion,
        UUID accountId
) {
    PokerHandshakeRequest(
            String roomId,
            String playerId,
            String playerName,
            Long expectedConnectionEpoch,
            String resumeToken,
            int protocolVersion,
            String buildVersion
    ) {
        this(roomId, playerId, playerName, "default", expectedConnectionEpoch, resumeToken,
                protocolVersion, buildVersion, null);
    }

    PokerHandshakeRequest(
            String roomId,
            String playerId,
            String playerName,
            Long expectedConnectionEpoch,
            String resumeToken
    ) {
        this(
                roomId,
                playerId,
                playerName,
                "default",
                expectedConnectionEpoch,
                resumeToken,
                ProtocolCompatibility.CURRENT_PROTOCOL_VERSION,
                ProtocolCompatibility.currentBuildVersion(),
                null
        );
    }

    boolean reconnecting() {
        return expectedConnectionEpoch != null;
    }
}
