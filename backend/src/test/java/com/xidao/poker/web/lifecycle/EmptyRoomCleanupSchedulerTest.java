package com.xidao.poker.web.lifecycle;

import com.xidao.poker.application.room.RoomService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmptyRoomCleanupSchedulerTest {
    @Test
    void periodicSweepRemovesAllExpiredEmptyRoomsAndCancelsOnShutdown() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        ArgumentCaptor<Runnable> sweep = ArgumentCaptor.forClass(Runnable.class);
        doReturn(future).when(taskScheduler)
                .scheduleWithFixedDelay(sweep.capture(), eq(Duration.ofSeconds(1)));
        RoomService rooms = mock(RoomService.class);
        when(rooms.removeRoomsEmptyFor(Duration.ofSeconds(20)))
                .thenReturn(List.of("empty-a", "empty-b"));

        EmptyRoomCleanupScheduler scheduler = new EmptyRoomCleanupScheduler(
                taskScheduler,
                rooms,
                Duration.ofSeconds(20),
                Duration.ofSeconds(1)
        );
        sweep.getValue().run();

        verify(rooms).removeRoomsEmptyFor(Duration.ofSeconds(20));
        scheduler.shutdown();
        verify(future).cancel(eq(false));
    }
}
