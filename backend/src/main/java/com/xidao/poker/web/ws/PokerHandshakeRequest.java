package com.xidao.poker.web.ws;

/** 握手完成后写入 WebSocket attributes，消息处理阶段不再读取可伪造的身份字段。 */
record PokerHandshakeRequest(
        String roomId,
        String playerId,
        String playerName,
        Long expectedConnectionEpoch,
        String resumeToken
) {
    boolean reconnecting() {
        return expectedConnectionEpoch != null;
    }
}
