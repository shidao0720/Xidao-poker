package com.xidao.poker.web.protocol;

/** resumeToken 只发送给其所属的精确 WebSocket，客户端应保存在当前会话中。 */
public record ConnectionReadyPayload(String playerId, long connectionEpoch, String resumeToken) {
}
