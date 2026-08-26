package com.xidao.poker.application.room;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * 使用共享 Executor 为每个房间维持单一 drain loop。没有“一房间一永久线程”，
 * 但同一房间的消息始终按 Runtime outbox 顺序交给网络适配器。
 */
public final class RoomEventDispatcher {
    private static final Logger log = LoggerFactory.getLogger(RoomEventDispatcher.class);

    private final RoomRegistry registry;
    private final RoomDeliverySink sink;
    private final Executor executor;

    public RoomEventDispatcher(RoomRegistry registry, RoomDeliverySink sink, Executor executor) {
        if (registry == null) throw new IllegalArgumentException("room registry is required");
        if (sink == null) throw new IllegalArgumentException("delivery sink is required");
        if (executor == null) throw new IllegalArgumentException("executor is required");
        this.registry = registry;
        this.sink = sink;
        this.executor = executor;
    }

    public void signal(String roomId) {
        RoomRuntime runtime = registry.require(roomId);
        if (runtime.tryStartDeliveryDrain()) {
            try {
                executor.execute(() -> drain(roomId, runtime));
            } catch (RuntimeException | Error error) {
                runtime.stopDeliveryDrain();
                throw error;
            }
        }
    }

    public boolean isIdle(String roomId) {
        return registry.find(roomId)
                .map(runtime -> !runtime.isDeliveryDrainRunning() && runtime.pendingDeliveryCount() == 0)
                .orElse(true);
    }

    private void drain(String roomId, RoomRuntime runtime) {
        try {
            while (true) {
                List<RoomDelivery> deliveries = runtime.drainOutbox();
                if (deliveries.isEmpty()) {
                    runtime.stopDeliveryDrain();
                    // 关闭 running 与新命令入队之间可能发生竞争；重新检查后由当前 worker 接管。
                    if (runtime.pendingDeliveryCount() > 0 && runtime.tryStartDeliveryDrain()) {
                        continue;
                    }
                    return;
                }
                for (RoomDelivery delivery : deliveries) {
                    try {
                        sink.deliver(roomId, delivery);
                    } catch (RuntimeException error) {
                        // 增量广播可能只送达部分连接，立即用 viewer-specific Snapshot 覆盖恢复。
                        if (delivery instanceof RoomDelivery.Events) {
                            runtime.enqueueRecoverySnapshots();
                        }
                        log.warn("ROOM_DELIVERY_FAILED roomId={} deliveryType={}",
                                roomId, delivery.getClass().getSimpleName(), error);
                    }
                }
            }
        } catch (Error fatal) {
            // 清理门闩后继续抛出 JVM 级错误，不能把 OOM 等致命状态伪装成可恢复发送失败。
            runtime.stopDeliveryDrain();
            log.error("ROOM_DELIVERY_DRAIN_FATAL roomId={}", roomId, fatal);
            throw fatal;
        } catch (RuntimeException error) {
            runtime.stopDeliveryDrain();
            log.error("ROOM_DELIVERY_DRAIN_ABORTED roomId={}", roomId, error);
            registry.find(roomId)
                    .filter(current -> current == runtime)
                    .filter(current -> current.pendingDeliveryCount() > 0)
                    .ifPresent(ignored -> signal(roomId));
        }
    }
}
