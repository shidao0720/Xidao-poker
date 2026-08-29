package com.xidao.poker.web.protocol;

import com.xidao.poker.application.room.EventReplay;
import com.xidao.poker.application.room.RoomExecutionResult;
import com.xidao.poker.engine.event.GameEvent;

/** 服务端统一出站信封；事件 type 直接使用稳定的 GameEventType 名称。 */
public record ServerEnvelope(
        int protocolVersion,
        String buildVersion,
        String type,
        String roomId,
        String requestId,
        Long sequence,
        Long handId,
        Long connectionEpoch,
        Object payload
) {
    private static ServerEnvelope message(
            String type,
            String roomId,
            String requestId,
            Long sequence,
            Long handId,
            Long connectionEpoch,
            Object payload
    ) {
        return new ServerEnvelope(
                ProtocolCompatibility.CURRENT_PROTOCOL_VERSION,
                ProtocolCompatibility.currentBuildVersion(),
                type,
                roomId,
                requestId,
                sequence,
                handId,
                connectionEpoch,
                payload
        );
    }

    public static ServerEnvelope event(String roomId, GameEvent event) {
        return message(
                event.type().name(), roomId, null, event.sequence(), event.handId(), null, event
        );
    }

    public static ServerEnvelope snapshot(
            String roomId,
            long connectionEpoch,
            String resumeToken,
            com.xidao.poker.engine.snapshot.GameSnapshot snapshot
    ) {
        return message(
                "ROOM_SNAPSHOT",
                roomId,
                null,
                snapshot.lastSequence(),
                snapshot.handId(),
                connectionEpoch,
                new SnapshotPayload(connectionEpoch, resumeToken, snapshot)
        );
    }

    public static ServerEnvelope connectionReady(
            String roomId,
            String playerId,
            long epoch,
            String resumeToken
    ) {
        return message(
                "CONNECTION_READY",
                roomId,
                null,
                null,
                null,
                epoch,
                new ConnectionReadyPayload(playerId, epoch, resumeToken)
        );
    }

    public static ServerEnvelope commandResult(
            String roomId,
            String requestId,
            RoomExecutionResult result
    ) {
        return message(
                "COMMAND_RESULT",
                roomId,
                requestId,
                result.lastSequence(),
                result.requesterSnapshot() == null ? null : result.requesterSnapshot().handId(),
                result.connectionEpoch(),
                new CommandResultPayload(result.duplicate(), result.ignored(), result.lastSequence())
        );
    }

    public static ServerEnvelope replay(String roomId, String requestId, EventReplay replay) {
        return message(
                "EVENT_REPLAY",
                roomId,
                requestId,
                replay.latestSequence(),
                null,
                null,
                replay
        );
    }

    public static ServerEnvelope error(String roomId, String requestId, String code, String message) {
        return message(
                "ERROR", roomId, requestId, null, null, null, new ErrorPayload(code, message)
        );
    }

    public static ServerEnvelope pong(String roomId, String requestId) {
        return message("PONG", roomId, requestId, null, null, null, null);
    }
}
