package com.xidao.poker.application.history;

import com.xidao.poker.test.HistoryFixtures;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncHandHistoryWriterTest {
    @Test
    void retriesRepositoryFailureWithoutRejectingTheRealtimeCommand() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch saved = new CountDownLatch(1);
        HandHistoryRepository repository = archive -> {
            if (attempts.incrementAndGet() < 3) throw new IllegalStateException("database unavailable");
            saved.countDown();
            return HandHistorySaveResult.SAVED;
        };

        try (AsyncHandHistoryWriter writer = writer(repository, 4, 3)) {
            assertThat(writer.publish(HistoryFixtures.archive(1))).isEqualTo(HistoryPublishResult.ACCEPTED);
            assertThat(saved.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(attempts).hasValue(3);
        }
    }

    @Test
    void boundedQueueReportsOverflowAndCloseRejectsNewHistory() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        HandHistoryRepository repository = archive -> {
            firstStarted.countDown();
            try {
                releaseFirst.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return HandHistorySaveResult.SAVED;
        };
        AsyncHandHistoryWriter writer = writer(repository, 1, 1);
        try {
            assertThat(writer.publish(HistoryFixtures.archive(1))).isEqualTo(HistoryPublishResult.ACCEPTED);
            assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(writer.publish(HistoryFixtures.archive(2))).isEqualTo(HistoryPublishResult.ACCEPTED);
            assertThat(writer.publish(HistoryFixtures.archive(3))).isEqualTo(HistoryPublishResult.QUEUE_FULL);
            assertThat(writer.queuedTasks()).isEqualTo(1);
        } finally {
            releaseFirst.countDown();
            writer.close();
        }
        assertThat(writer.publish(HistoryFixtures.archive(4))).isEqualTo(HistoryPublishResult.CLOSED);
    }

    private static AsyncHandHistoryWriter writer(
            HandHistoryRepository repository,
            int queueCapacity,
            int maximumAttempts
    ) {
        return new AsyncHandHistoryWriter(
                repository,
                1,
                queueCapacity,
                maximumAttempts,
                Duration.ofMillis(1),
                Duration.ofSeconds(5)
        );
    }
}
