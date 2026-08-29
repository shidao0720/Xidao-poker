package com.xidao.poker.web.ws;

import com.xidao.poker.web.protocol.ProtocolCompatibility;

/** 握手完成后写入 WebSocket attributes，消息处理阶段不再读取可伪造的身份字段。 */
record PokerHandshakeRequest(
        String roomId,
        String playerId,
        String playerName,
        Long expectedConnectionEpoch,
        String resumeToken,
        int protocolVersion,
        String buildVersion
) {
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
                expectedConnectionEpoch,
                resumeToken,
                ProtocolCompatibility.CURRENT_PROTOCOL_VERSION,
                ProtocolCompatibility.currentBuildVersion()
        );
    }

    boolean reconnecting() {
        return expectedConnectionEpoch != null;
    }
}
