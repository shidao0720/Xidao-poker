package com.xidao.poker.web.protocol;

import com.xidao.poker.application.room.EventReplay;
import com.xidao.poker.application.room.RoomExecutionResult;
import com.xidao.poker.engine.event.GameEvent;

/** 服务端统一出站信封；事件 type 直接使用稳定的 GameEventType 名称。 */
public record ServerEnvelope(
        String type,
        String roomId,
        String commandId,
        Long sequence,
        Long handId,
        Long connectionEpoch,
        Object payload
) {
    public static ServerEnvelope event(String roomId, GameEvent event) {
        return new ServerEnvelope(
                event.type().name(), roomId, null, event.sequence(), event.handId(), null, event
        );
    }

    public static ServerEnvelope snapshot(
            String roomId,
            long connectionEpoch,
            String resumeToken,
            com.xidao.poker.engine.snapshot.GameSnapshot snapshot
    ) {
        return new ServerEnvelope(
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
        return new ServerEnvelope(
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
            String commandId,
            RoomExecutionResult result
    ) {
        return new ServerEnvelope(
                "COMMAND_RESULT",
                roomId,
                commandId,
                result.lastSequence(),
                result.requesterSnapshot() == null ? null : result.requesterSnapshot().handId(),
                result.connectionEpoch(),
                new CommandResultPayload(result.duplicate(), result.ignored(), result.lastSequence())
        );
    }

    public static ServerEnvelope replay(String roomId, String commandId, EventReplay replay) {
        return new ServerEnvelope(
                "EVENT_REPLAY",
                roomId,
                commandId,
                replay.latestSequence(),
                null,
                null,
                replay
        );
    }

    public static ServerEnvelope error(String roomId, String commandId, String code, String message) {
        return new ServerEnvelope(
                "ERROR", roomId, commandId, null, null, null, new ErrorPayload(code, message)
        );
    }

    public static ServerEnvelope pong(String roomId, String commandId) {
        return new ServerEnvelope("PONG", roomId, commandId, null, null, null, null);
    }
}
