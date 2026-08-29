package com.xidao.poker.web.lifecycle;

import com.xidao.poker.application.command.TurnTimeoutCommand;
import com.xidao.poker.application.room.GameApplicationService;
import com.xidao.poker.application.room.RoomExecutionResult;
import com.xidao.poker.application.room.RoomTurnTimerTarget;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TurnTimeoutSchedulerTest {
    @Test
    void committedMutationSchedulesCapturedHandAndTurnAndSubmitsOneTimerCommand() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        ArgumentCaptor<Runnable> runnable = ArgumentCaptor.forClass(Runnable.class);
        doReturn(future).when(taskScheduler).schedule(runnable.capture(), any(Instant.class));
        GameApplicationService games = mock(GameApplicationService.class);
        when(games.turnTimerTarget("room")).thenReturn(Optional.of(
                new RoomTurnTimerTarget("room", "A", 7, 11)));
        when(games.executeTimer(any(TurnTimeoutCommand.class))).thenReturn(
                new RoomExecutionResult(false, false, 1, 5, List.of(), null));

        TurnTimeoutScheduler scheduler = new TurnTimeoutScheduler(
                taskScheduler,
                games,
                Duration.ofSeconds(30),
                Clock.fixed(Instant.parse("2026-08-29T00:00:00Z"), ZoneOffset.UTC)
        );
        scheduler.reconcile("room");
        runnable.getValue().run();

        ArgumentCaptor<TurnTimeoutCommand> command = ArgumentCaptor.forClass(TurnTimeoutCommand.class);
        verify(games).executeTimer(command.capture());
        assertThat(command.getValue()).isEqualTo(
                new TurnTimeoutCommand("room", "turn-timeout-7-11", "A", 7, 11));
        assertThat(scheduler.pendingTaskCount()).isZero();
    }

    @Test
    void advancingTurnCancelsThePreviousTimerBeforeSchedulingTheNextOne() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> firstFuture = mock(ScheduledFuture.class);
        ScheduledFuture<?> secondFuture = mock(ScheduledFuture.class);
        doReturn(firstFuture, secondFuture).when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
        GameApplicationService games = mock(GameApplicationService.class);
        when(games.turnTimerTarget("room"))
                .thenReturn(Optional.of(new RoomTurnTimerTarget("room", "A", 1, 1)))
                .thenReturn(Optional.of(new RoomTurnTimerTarget("room", "B", 1, 2)));

        TurnTimeoutScheduler scheduler = new TurnTimeoutScheduler(
                taskScheduler, games, Duration.ofSeconds(30), Clock.systemUTC());
        scheduler.reconcile("room");
        scheduler.reconcile("room");

        verify(firstFuture).cancel(eq(false));
        assertThat(scheduler.pendingTaskCount()).isEqualTo(1);
    }
}
