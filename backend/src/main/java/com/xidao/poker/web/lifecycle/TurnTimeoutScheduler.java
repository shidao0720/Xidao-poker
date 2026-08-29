package com.xidao.poker.web.lifecycle;

import com.xidao.poker.application.command.TurnTimeoutCommand;
import com.xidao.poker.application.room.GameApplicationService;
import com.xidao.poker.application.room.RoomApplicationException;
import com.xidao.poker.application.room.RoomMutationListener;
import com.xidao.poker.application.room.RoomTurnTimerTarget;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/** 每次已提交房间变更后，对齐唯一的当前行动计时器。 */
public final class TurnTimeoutScheduler implements RoomMutationListener {
    private static final Logger log = LoggerFactory.getLogger(TurnTimeoutScheduler.class);

    private final TaskScheduler scheduler;
    private final GameApplicationService games;
    private final Duration timeout;
    private final Clock clock;
    private final Map<String, ScheduledTurn> tasks = new ConcurrentHashMap<>();

    public TurnTimeoutScheduler(
            TaskScheduler scheduler,
            GameApplicationService games,
            Duration timeout,
            Clock clock
    ) {
        if (scheduler == null || games == null || clock == null) {
            throw new IllegalArgumentException("turn timer dependencies are required");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("turn timeout must be positive");
        }
        this.scheduler = scheduler;
        this.games = games;
        this.timeout = timeout;
        this.clock = clock;
        games.addMutationListener(this);
    }

    @Override
    public void afterCommittedMutation(String roomId, com.xidao.poker.application.room.RoomExecutionResult result) {
        reconcile(roomId);
    }

    void reconcile(String roomId) {
        Optional<RoomTurnTimerTarget> target;
        try {
            target = games.turnTimerTarget(roomId);
        } catch (RoomApplicationException missingRoom) {
            cancel(roomId);
            return;
        }
        if (target.isEmpty()) {
            cancel(roomId);
            return;
        }

        RoomTurnTimerTarget value = target.orElseThrow();
        TurnTimeoutCommand command = new TurnTimeoutCommand(
                value.roomId(),
                "turn-timeout-" + value.handId() + "-" + value.turnId(),
                value.playerId(),
                value.handId(),
                value.turnId()
        );
        tasks.compute(roomId, (ignored, previous) -> {
            if (previous != null && previous.command().equals(command)) return previous;
            if (previous != null) previous.future().cancel(false);
            ScheduledFuture<?> future = scheduler.schedule(
                    () -> fire(command), Instant.now(clock).plus(timeout));
            if (future == null) throw new IllegalStateException("turn timeout was not scheduled");
            log.info("TURN_TIMEOUT_SCHEDULED roomId={} handId={} turnId={} playerId={} timeoutSeconds={}",
                    command.roomId(), command.handId(), command.turnId(), command.playerId(), timeout.toSeconds());
            return new ScheduledTurn(command, future);
        });
    }

    private void fire(TurnTimeoutCommand command) {
        ScheduledTurn current = tasks.get(command.roomId());
        if (current == null || !current.command().equals(command)) return;
        try {
            games.executeTimer(command);
        } catch (RoomApplicationException error) {
            log.debug("TURN_TIMEOUT_IGNORED roomId={} handId={} turnId={} code={}",
                    command.roomId(), command.handId(), command.turnId(), error.code());
        } catch (RuntimeException error) {
            log.error("TURN_TIMEOUT_FAILED roomId={} handId={} turnId={} playerId={}",
                    command.roomId(), command.handId(), command.turnId(), command.playerId(), error);
        } finally {
            tasks.computeIfPresent(command.roomId(), (ignored, scheduled) ->
                    scheduled.command().equals(command) ? null : scheduled);
        }
    }

    private void cancel(String roomId) {
        ScheduledTurn removed = tasks.remove(roomId);
        if (removed != null) removed.future().cancel(false);
    }

    @PreDestroy
    void shutdown() {
        tasks.values().forEach(task -> task.future().cancel(false));
        tasks.clear();
    }

    int pendingTaskCount() {
        return tasks.size();
    }

    private record ScheduledTurn(TurnTimeoutCommand command, ScheduledFuture<?> future) {
    }
}
