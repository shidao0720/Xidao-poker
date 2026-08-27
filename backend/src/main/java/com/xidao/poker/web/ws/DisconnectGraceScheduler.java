package com.xidao.poker.web.ws;

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
        tasks.compute(key, (ignored, previous) -> {
            if (previous != null) previous.future().cancel(false);
            ScheduledFuture<?> future = scheduler.schedule(
                    () -> expire(key, identity.connectionEpoch()),
                    Instant.now(clock).plus(grace)
            );
            if (future == null) throw new IllegalStateException("disconnect timeout was not scheduled");
            return new ScheduledDisconnect(identity.connectionEpoch(), future);
        });
        log.info("DISCONNECT_GRACE_SCHEDULED roomId={} playerId={} epoch={} graceSeconds={}",
                identity.roomId(), identity.playerId(), identity.connectionEpoch(), grace.toSeconds());
    }

    public void cancel(String roomId, String playerId) {
        ScheduledDisconnect removed = tasks.remove(new MemberKey(roomId, playerId));
        if (removed != null) removed.future().cancel(false);
    }

    private void expire(MemberKey key, long epoch) {
        try {
            games.expireDisconnected(
                    key.roomId(),
                    "disconnect-expired-" + epoch,
                    key.playerId(),
                    epoch
            );
            connections.forgetMember(key.roomId(), key.playerId(), epoch);
            log.info("DISCONNECT_GRACE_EXPIRED roomId={} playerId={} epoch={}",
                    key.roomId(), key.playerId(), epoch);
        } catch (RoomApplicationException error) {
            // 房间/成员已由其他路径清理时，epoch 校验仍保证不会删除新连接。
            connections.forgetMember(key.roomId(), key.playerId(), epoch);
            log.debug("DISCONNECT_EXPIRY_IGNORED roomId={} playerId={} epoch={} code={}",
                    key.roomId(), key.playerId(), epoch, error.code());
        } catch (RuntimeException error) {
            log.error("DISCONNECT_EXPIRY_FAILED roomId={} playerId={} epoch={}",
                    key.roomId(), key.playerId(), epoch, error);
        } finally {
            tasks.computeIfPresent(key, (ignored, current) -> current.epoch() == epoch ? null : current);
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

    private record ScheduledDisconnect(long epoch, ScheduledFuture<?> future) {
    }
}
