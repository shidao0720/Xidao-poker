package com.xidao.poker.web.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xidao.poker.application.room.RoomDelivery;
import com.xidao.poker.application.room.RoomDeliverySink;
import com.xidao.poker.engine.event.GameEvent;
import com.xidao.poker.web.protocol.ServerEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 把应用层 delivery 翻译为网络消息；不做任何游戏状态决策。 */
public final class WebSocketRoomDeliverySink implements RoomDeliverySink {
    private static final Logger log = LoggerFactory.getLogger(WebSocketRoomDeliverySink.class);

    private final WebSocketConnectionRegistry connections;
    private final ObjectMapper objectMapper;

    public WebSocketRoomDeliverySink(WebSocketConnectionRegistry connections, ObjectMapper objectMapper) {
        this.connections = connections;
        this.objectMapper = objectMapper;
    }

    @Override
    public void deliver(String roomId, RoomDelivery delivery) {
        if (delivery instanceof RoomDelivery.Events events) {
            for (GameEvent event : events.events()) {
                connections.broadcast(roomId, json(ServerEnvelope.event(roomId, event)));
                log.debug("WS_MESSAGE_SENT roomId={} target=BROADCAST type={} sequence={}",
                        roomId, event.type(), event.sequence());
            }
            return;
        }
        if (delivery instanceof RoomDelivery.Snapshot snapshot) {
            String token = connections.resumeToken(
                    roomId,
                    snapshot.playerId(),
                    snapshot.connectionId(),
                    snapshot.connectionEpoch()
            ).orElse(null);
            if (token == null) {
                log.debug("STALE_SNAPSHOT_DROPPED roomId={} playerId={} connectionId={} epoch={}",
                        roomId, snapshot.playerId(), snapshot.connectionId(), snapshot.connectionEpoch());
                return;
            }
            boolean sent = connections.sendExact(
                    roomId,
                    snapshot.playerId(),
                    snapshot.connectionId(),
                    snapshot.connectionEpoch(),
                    json(ServerEnvelope.snapshot(
                            roomId,
                            snapshot.connectionEpoch(),
                            token,
                            snapshot.snapshot()
                    ))
            );
            if (sent) {
                log.debug("WS_MESSAGE_SENT roomId={} playerId={} connectionId={} type=ROOM_SNAPSHOT sequence={}",
                        roomId, snapshot.playerId(), snapshot.connectionId(), snapshot.snapshot().lastSequence());
            }
        }
    }

    private String json(ServerEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("server message serialization failed", error);
        }
    }
}
