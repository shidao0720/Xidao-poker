package com.xidao.poker.web.protocol;

import com.fasterxml.jackson.databind.JsonNode;

/** roomId/playerId/connection 信息不在消息体中接收，只使用握手绑定。 */
public record ClientEnvelope(String type, String commandId, JsonNode payload) {
}
