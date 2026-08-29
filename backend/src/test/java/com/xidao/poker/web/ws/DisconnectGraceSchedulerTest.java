package com.xidao.poker.web.ws;

import com.xidao.poker.application.command.DisconnectExpiryCommand;
import com.xidao.poker.application.command.RoomTimerScope;
import com.xidao.poker.application.room.GameApplicationService;
import com.xidao.poker.application.room.RoomExecutionResult;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.WebSocketSession;

import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DisconnectGraceSchedulerTest {
    @Test
    void expiryUsesCapturedEpochAndRemovesOnlyThatReconnectTicket() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        var runnable = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        doReturn(future).when(taskScheduler).schedule(runnable.capture(), any(Instant.class));
        GameApplicationService games = mock(GameApplicationService.class);
        when(games.timerScope("room")).thenReturn(new RoomTimerScope("room", 7, 11));
        var command = org.mockito.ArgumentCaptor.forClass(DisconnectExpiryCommand.class);
        when(games.executeTimer(command.capture()))
                .thenReturn(new RoomExecutionResult(false, false, 1, 3, List.of(), null));
        WebSocketConnectionRegistry connections = new WebSocketConnectionRegistry(10_000, 1_048_576);
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("socket-A");
        when(session.isOpen()).thenReturn(true);
        WebSocketConnectionRegistry.PreparedConnection prepared = connections.prepare(
                session,
                new PokerHandshakeRequest("room", "A", "Alice", null, null)
        );
        connections.commit(prepared);
        DisconnectGraceScheduler scheduler = new DisconnectGraceScheduler(
                taskScheduler,
                games,
                connections,
                Duration.ofSeconds(30),
                Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC)
        );

        scheduler.schedule(prepared.identity());
        assertThat(scheduler.pendingTaskCount()).isEqualTo(1);
        runnable.getValue().run();

        verify(games).executeTimer(any(DisconnectExpiryCommand.class));
        assertThat(command.getValue()).isEqualTo(new DisconnectExpiryCommand(
                "room", "disconnect-expired-1", "A", 1, 7, 11));
        assertThat(connections.resumeToken("room", "A", "socket-A", 1)).isEmpty();
        assertThat(scheduler.pendingTaskCount()).isZero();
    }

    @Test
    void reconnectCancellationPreventsPendingTimeout() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(future).when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
        GameApplicationService games = mock(GameApplicationService.class);
        when(games.timerScope("room")).thenReturn(new RoomTimerScope("room", 0, 0));
        DisconnectGraceScheduler scheduler = new DisconnectGraceScheduler(
                taskScheduler,
                games,
                new WebSocketConnectionRegistry(10_000, 1_048_576),
                Duration.ofSeconds(30),
                Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC)
        );
        SocketIdentity identity = new SocketIdentity("room", "A", "Alice", "socket-A", 1, "token");

        scheduler.schedule(identity);
        scheduler.cancel("room", "A");

        verify(future).cancel(eq(false));
        assertThat(scheduler.pendingTaskCount()).isZero();
    }
}
