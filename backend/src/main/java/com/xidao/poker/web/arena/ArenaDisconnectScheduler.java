package com.xidao.poker.web.arena;

import com.xidao.poker.application.arena.ArenaApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class ArenaDisconnectScheduler {
    private static final Logger log = LoggerFactory.getLogger(ArenaDisconnectScheduler.class);
    private static final long GRACE_SECONDS = 20;
    private final ScheduledExecutorService scheduler;
    private final ArenaApplicationService arenas;
    private final ArenaConnectionRegistry connections;

    public ArenaDisconnectScheduler(ScheduledExecutorService scheduler, ArenaApplicationService arenas,
                                    ArenaConnectionRegistry connections) {
        this.scheduler = scheduler;
        this.arenas = arenas;
        this.connections = connections;
    }

    public void schedule(ArenaSocketIdentity identity) {
        scheduler.schedule(() -> {
            try {
                arenas.expireDisconnect(identity.roomId(), identity.playerId(), identity.connectionEpoch());
                connections.forgetIfCurrent(identity.roomId(), identity.playerId(), identity.connectionEpoch());
                log.info("ARENA_DISCONNECT_EXPIRED roomId={} playerId={} epoch={}",
                        identity.roomId(), identity.playerId(), identity.connectionEpoch());
            } catch (RuntimeException ignored) {
                // A newer authenticated connection or a removed room makes this timer stale.
            }
        }, GRACE_SECONDS, TimeUnit.SECONDS);
    }
}
