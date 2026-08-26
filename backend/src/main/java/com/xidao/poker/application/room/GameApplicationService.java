package com.xidao.poker.application.room;

import com.xidao.poker.application.command.PlayerActionCommand;
import com.xidao.poker.application.command.StartGameCommand;
import com.xidao.poker.application.command.TurnTimeoutCommand;
import com.xidao.poker.engine.action.IllegalActionException;
import com.xidao.poker.engine.snapshot.GameSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * WebSocket Handler 的唯一游戏入口。身份信息应由连接绑定关系传入，Handler 不得直接调用 GameSession。
 */
public final class GameApplicationService {
    private static final Logger log = LoggerFactory.getLogger(GameApplicationService.class);

    private final RoomRegistry registry;
    private final RoomEventDispatcher dispatcher;

    public GameApplicationService(RoomRegistry registry, RoomEventDispatcher dispatcher) {
        if (registry == null) throw new IllegalArgumentException("room registry is required");
        if (dispatcher == null) throw new IllegalArgumentException("room event dispatcher is required");
        this.registry = registry;
        this.dispatcher = dispatcher;
    }

    public RoomExecutionResult join(
            String roomId,
            String commandId,
            String playerId,
            String playerName,
            String connectionId
    ) {
        return executeMutation("JOIN", roomId, commandId, playerId,
                () -> runtime(roomId).join(commandId, playerId, playerName, connectionId));
    }

    public RoomExecutionResult setReady(
            String roomId,
            String commandId,
            String playerId,
            String connectionId,
            long connectionEpoch,
            boolean ready
    ) {
        return executeMutation("SET_READY", roomId, commandId, playerId,
                () -> runtime(roomId).setReady(
                        commandId, playerId, connectionId, connectionEpoch, ready));
    }

    public RoomExecutionResult startGame(StartGameCommand command) {
        return executeMutation("START_GAME", command.roomId(), command.commandId(), command.playerId(),
                () -> runtime(command.roomId()).startGame(command));
    }

    public RoomExecutionResult act(PlayerActionCommand command) {
        return executeMutation(command.action().name(), command.roomId(), command.commandId(), command.playerId(),
                () -> runtime(command.roomId()).act(command));
    }

    public RoomExecutionResult disconnect(
            String roomId,
            String commandId,
            String playerId,
            String connectionId,
            long connectionEpoch
    ) {
        return executeMutation("DISCONNECT", roomId, commandId, playerId,
                () -> runtime(roomId).disconnect(
                        commandId, playerId, connectionId, connectionEpoch));
    }

    public RoomExecutionResult reconnect(
            String roomId,
            String commandId,
            String playerId,
            long expectedConnectionEpoch,
            String newConnectionId
    ) {
        return executeMutation("RECONNECT", roomId, commandId, playerId,
                () -> runtime(roomId).reconnect(
                        commandId, playerId, expectedConnectionEpoch, newConnectionId));
    }

    public RoomExecutionResult leave(
            String roomId,
            String commandId,
            String playerId,
            String connectionId,
            long connectionEpoch
    ) {
        return executeMutation("LEAVE", roomId, commandId, playerId,
                () -> runtime(roomId).leave(
                        commandId, playerId, connectionId, connectionEpoch));
    }

    public RoomExecutionResult expireDisconnected(
            String roomId,
            String commandId,
            String playerId,
            long connectionEpoch
    ) {
        return executeMutation("DISCONNECT_EXPIRED", roomId, commandId, playerId,
                () -> runtime(roomId).expireDisconnected(commandId, playerId, connectionEpoch));
    }

    public RoomExecutionResult timeout(TurnTimeoutCommand command) {
        return executeMutation("TURN_TIMEOUT", command.roomId(), command.commandId(), command.playerId(),
                () -> runtime(command.roomId()).timeout(command));
    }

    public GameSnapshot requestSnapshot(
            String roomId,
            String playerId,
            String connectionId,
            long connectionEpoch
    ) {
        return executeReadWithDelivery("REQUEST_SNAPSHOT", roomId, playerId, () ->
                runtime(roomId).requestSnapshot(playerId, connectionId, connectionEpoch));
    }

    public EventReplay replayAfter(
            String roomId,
            String playerId,
            String connectionId,
            long connectionEpoch,
            long afterSequence
    ) {
        return executeRead("REPLAY_EVENTS", roomId, playerId, () ->
                runtime(roomId).replayAfter(playerId, connectionId, connectionEpoch, afterSequence));
    }

    private RoomExecutionResult executeMutation(
            String operation,
            String roomId,
            String commandId,
            String playerId,
            Supplier<RoomExecutionResult> command
    ) {
        log.debug("ROOM_COMMAND_RECEIVED roomId={} playerId={} commandId={} operation={}",
                roomId, playerId, commandId, operation);
        RoomExecutionResult result;
        try {
            result = command.get();
        } catch (RuntimeException error) {
            log.warn("ROOM_COMMAND_REJECTED roomId={} playerId={} commandId={} operation={} code={} message={}",
                    roomId, playerId, commandId, operation, errorCode(error), error.getMessage());
            throw error;
        }
        long handId = result.requesterSnapshot() == null ? 0 : result.requesterSnapshot().handId();
        log.info("ROOM_COMMAND_COMMITTED roomId={} handId={} playerId={} commandId={} operation={} "
                        + "sequence={} duplicate={} ignored={} eventCount={}",
                roomId, handId, playerId, commandId, operation, result.lastSequence(),
                result.duplicate(), result.ignored(), result.events().size());
        try {
            dispatcher.signal(roomId);
        } catch (RuntimeException error) {
            log.error("ROOM_DELIVERY_SIGNAL_FAILED roomId={} playerId={} commandId={} operation={} sequence={}",
                    roomId, playerId, commandId, operation, result.lastSequence(), error);
            throw error;
        }
        return result;
    }

    private <T> T executeReadWithDelivery(
            String operation,
            String roomId,
            String playerId,
            Supplier<T> query
    ) {
        T result = executeRead(operation, roomId, playerId, query);
        try {
            dispatcher.signal(roomId);
        } catch (RuntimeException error) {
            log.error("ROOM_DELIVERY_SIGNAL_FAILED roomId={} playerId={} operation={}",
                    roomId, playerId, operation, error);
            throw error;
        }
        return result;
    }

    private <T> T executeRead(String operation, String roomId, String playerId, Supplier<T> query) {
        try {
            T result = query.get();
            log.debug("ROOM_QUERY_COMPLETED roomId={} playerId={} operation={}",
                    roomId, playerId, operation);
            return result;
        } catch (RuntimeException error) {
            log.warn("ROOM_QUERY_REJECTED roomId={} playerId={} operation={} code={} message={}",
                    roomId, playerId, operation, errorCode(error), error.getMessage());
            throw error;
        }
    }

    private Object errorCode(RuntimeException error) {
        if (error instanceof IllegalActionException illegalAction) return illegalAction.code();
        if (error instanceof RoomApplicationException applicationError) return applicationError.code();
        return error.getClass().getSimpleName();
    }

    private RoomRuntime runtime(String roomId) {
        return registry.require(roomId);
    }
}
