package com.xidao.poker.web.lifecycle;

import com.xidao.poker.application.room.RoomService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

/** 周期清理超过 TTL 的空房间；最终删除仍由 RoomRuntime 的原子关闭条件裁决。 */
public final class EmptyRoomCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(EmptyRoomCleanupScheduler.class);

    private final RoomService rooms;
    private final Duration emptyTtl;
    private final ScheduledFuture<?> task;

    public EmptyRoomCleanupScheduler(
            TaskScheduler scheduler,
            RoomService rooms,
            Duration emptyTtl,
            Duration cleanupInterval
    ) {
        if (scheduler == null) throw new IllegalArgumentException("room cleanup scheduler is required");
        if (rooms == null) throw new IllegalArgumentException("room service is required");
        if (emptyTtl == null || emptyTtl.isNegative() || emptyTtl.isZero()) {
            throw new IllegalArgumentException("empty-room ttl must be positive");
        }
        if (cleanupInterval == null || cleanupInterval.isNegative() || cleanupInterval.isZero()) {
            throw new IllegalArgumentException("cleanup interval must be positive");
        }
        this.rooms = rooms;
        this.emptyTtl = emptyTtl;
        this.task = scheduler.scheduleWithFixedDelay(this::sweep, cleanupInterval);
        if (task == null) throw new IllegalStateException("empty-room cleanup was not scheduled");
    }

    void sweep() {
        try {
            List<String> removed = rooms.removeRoomsEmptyFor(emptyTtl);
            for (String roomId : removed) {
                log.info("EMPTY_ROOM_REMOVED roomId={} emptyTtlSeconds={}", roomId, emptyTtl.toSeconds());
            }
        } catch (RuntimeException error) {
            log.error("EMPTY_ROOM_CLEANUP_FAILED code={}", error.getClass().getSimpleName(), error);
        }
    }

    @PreDestroy
    void shutdown() {
        task.cancel(false);
    }
}
