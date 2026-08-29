package com.xidao.poker.web.ws;

import com.xidao.poker.application.command.DisconnectExpiryCommand;
import com.xidao.poker.application.command.RoomTimerScope;
import com.xidao.poker.application.room.GameApplicationService;
import com.xidao.poker.application.room.RoomApplicationException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/** 30 秒断线宽限只存在于应用适配层；过期任务依靠 epoch 保证无害。 */
public final class DisconnectGraceScheduler {
    private static final Logger log = LoggerFactory.getLogger(DisconnectGraceScheduler.class);

    private final TaskScheduler scheduler;
    private final GameApplicationService games;
    private final WebSocketConnectionRegistry connections;
    private final Duration grace;
    private final Clock clock;
    private final Map<MemberKey, ScheduledDisconnect> tasks = new ConcurrentHashMap<>();

    public DisconnectGraceScheduler(
            TaskScheduler scheduler,
            GameApplicationService games,
            WebSocketConnectionRegistry connections,
            Duration grace,
            Clock clock
    ) {
        this.scheduler = scheduler;
        this.games = games;
        this.connections = connections;
        this.grace = grace;
        this.clock = clock;
    }

    public void schedule(SocketIdentity identity) {
        MemberKey key = new MemberKey(identity.roomId(), identity.playerId());
        RoomTimerScope scope = games.timerScope(identity.roomId());
        DisconnectExpiryCommand command = new DisconnectExpiryCommand(
                identity.roomId(),
                "disconnect-expired-" + identity.connectionEpoch(),
                identity.playerId(),
                identity.connectionEpoch(),
                scope.handId(),
                scope.turnId()
        );
        tasks.compute(key, (ignored, previous) -> {
            if (previous != null) previous.future().cancel(false);
            ScheduledFuture<?> future = scheduler.schedule(
                    () -> expire(key, command),
                    Instant.now(clock).plus(grace)
            );
            if (future == null) throw new IllegalStateException("disconnect timeout was not scheduled");
            return new ScheduledDisconnect(command, future);
        });
        log.info("DISCONNECT_GRACE_SCHEDULED roomId={} handId={} turnId={} playerId={} epoch={} graceSeconds={}",
                identity.roomId(), command.handId(), command.turnId(), identity.playerId(),
                identity.connectionEpoch(), grace.toSeconds());
    }

    public void cancel(String roomId, String playerId) {
        ScheduledDisconnect removed = tasks.remove(new MemberKey(roomId, playerId));
        if (removed != null) removed.future().cancel(false);
    }

    private void expire(MemberKey key, DisconnectExpiryCommand command) {
        try {
            games.executeTimer(command);
            connections.forgetMember(key.roomId(), key.playerId(), command.connectionEpoch());
            log.info("DISCONNECT_GRACE_EXPIRED roomId={} handId={} turnId={} playerId={} epoch={}",
                    key.roomId(), command.handId(), command.turnId(), key.playerId(),
                    command.connectionEpoch());
        } catch (RoomApplicationException error) {
            // 房间/成员已由其他路径清理时，epoch 校验仍保证不会删除新连接。
            connections.forgetMember(key.roomId(), key.playerId(), command.connectionEpoch());
            log.debug("DISCONNECT_EXPIRY_IGNORED roomId={} playerId={} epoch={} code={}",
                    key.roomId(), key.playerId(), command.connectionEpoch(), error.code());
        } catch (RuntimeException error) {
            log.error("DISCONNECT_EXPIRY_FAILED roomId={} playerId={} epoch={}",
                    key.roomId(), key.playerId(), command.connectionEpoch(), error);
        } finally {
            tasks.computeIfPresent(key, (ignored, current) ->
                    current.command().commandId().equals(command.commandId()) ? null : current);
        }
    }

    @PreDestroy
    void shutdown() {
        tasks.values().forEach(task -> task.future().cancel(false));
        tasks.clear();
    }

    int pendingTaskCount() {
        return tasks.size();
    }

    private record MemberKey(String roomId, String playerId) {
    }

    private record ScheduledDisconnect(DisconnectExpiryCommand command, ScheduledFuture<?> future) {
    }
}
