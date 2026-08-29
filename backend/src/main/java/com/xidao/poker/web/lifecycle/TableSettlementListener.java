package com.xidao.poker.web.lifecycle;

import com.xidao.poker.application.account.TableEconomyService;
import com.xidao.poker.application.room.GameApplicationService;
import com.xidao.poker.application.room.RoomExecutionResult;
import com.xidao.poker.application.room.RoomMutationListener;
import com.xidao.poker.engine.event.GameEvent;
import com.xidao.poker.engine.event.GameEventType;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/** Settles table escrow from authoritative PLAYER_LEFT events and retries transient database failures. */
public final class TableSettlementListener implements RoomMutationListener {
    private static final Logger log = LoggerFactory.getLogger(TableSettlementListener.class);
    private static final Duration RETRY_DELAY = Duration.ofSeconds(2);

    private final TableEconomyService economy;
    private final TaskScheduler scheduler;
    private final Clock clock;
    private final Map<String, PendingSettlement> pending = new ConcurrentHashMap<>();

    public TableSettlementListener(
            TableEconomyService economy,
            TaskScheduler scheduler,
            GameApplicationService games,
            Clock clock
    ) {
        this.economy = economy;
        this.scheduler = scheduler;
        this.clock = clock;
        games.addMutationListener(this);
    }

    @Override
    public void afterCommittedMutation(String roomId, RoomExecutionResult result) {
        for (GameEvent event : result.events()) {
            if (event.type() != GameEventType.PLAYER_LEFT || event.playerId() == null) continue;
            Object value = event.data().get("stack");
            if (!(value instanceof Number stack)) {
                log.error("TABLE_SETTLEMENT_EVENT_INVALID roomId={} playerId={} sequence={}",
                        roomId, event.playerId(), event.sequence());
                continue;
            }
            PendingSettlement settlement = new PendingSettlement(
                    roomId,
                    event.playerId(),
                    stack.longValue(),
                    requestId(roomId, event.playerId(), event.sequence()),
                    null
            );
            settle(settlement);
        }
    }

    private void settle(PendingSettlement settlement) {
        String key = settlement.roomId() + '\n' + settlement.gameId();
        try {
            economy.settleSeat(settlement.roomId(), settlement.gameId(),
                    settlement.finalStack(), settlement.requestId());
            PendingSettlement removed = pending.remove(key);
            if (removed != null && removed.future() != null) removed.future().cancel(false);
            log.info("TABLE_ESCROW_SETTLED roomId={} playerId={} finalStack={}",
                    settlement.roomId(), settlement.gameId(), settlement.finalStack());
        } catch (RuntimeException error) {
            log.error("TABLE_ESCROW_SETTLEMENT_RETRY roomId={} playerId={} code={}",
                    settlement.roomId(), settlement.gameId(), error.getClass().getSimpleName());
            pending.compute(key, (ignored, previous) -> {
                if (previous != null && previous.future() != null && !previous.future().isDone()) return previous;
                ScheduledFuture<?> future = scheduler.schedule(
                        () -> retry(key), Instant.now(clock).plus(RETRY_DELAY));
                return new PendingSettlement(settlement.roomId(), settlement.gameId(),
                        settlement.finalStack(), settlement.requestId(), future);
            });
        }
    }

    private void retry(String key) {
        PendingSettlement settlement = pending.get(key);
        if (settlement == null) return;
        pending.put(key, new PendingSettlement(settlement.roomId(), settlement.gameId(),
                settlement.finalStack(), settlement.requestId(), null));
        settle(pending.get(key));
    }

    private static String requestId(String roomId, String gameId, long sequence) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((roomId + '\n' + gameId + '\n' + sequence).getBytes(StandardCharsets.UTF_8));
            return "cashout-" + HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    @PreDestroy
    void shutdown() {
        pending.values().forEach(value -> {
            if (value.future() != null) value.future().cancel(false);
        });
        pending.clear();
    }

    private record PendingSettlement(
            String roomId,
            String gameId,
            long finalStack,
            String requestId,
            ScheduledFuture<?> future
    ) { }
}
