package com.xidao.poker.application.history;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 有界、非阻塞的历史写入器。数据库慢或不可用时不会占用房间锁，也不会让队列无限增长。
 */
public final class AsyncHandHistoryWriter implements HandHistoryPublisher, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(AsyncHandHistoryWriter.class);

    private final HandHistoryRepository repository;
    private final ThreadPoolExecutor executor;
    private final int maximumAttempts;
    private final Duration initialRetryBackoff;
    private final Duration shutdownWait;
    private final AtomicBoolean closed = new AtomicBoolean();

    public AsyncHandHistoryWriter(
            HandHistoryRepository repository,
            int writerThreads,
            int queueCapacity,
            int maximumAttempts,
            Duration initialRetryBackoff,
            Duration shutdownWait
    ) {
        this.repository = Objects.requireNonNull(repository, "history repository");
        if (writerThreads <= 0) throw new IllegalArgumentException("writer threads must be positive");
        if (queueCapacity <= 0) throw new IllegalArgumentException("queue capacity must be positive");
        if (maximumAttempts <= 0) throw new IllegalArgumentException("maximum attempts must be positive");
        if (initialRetryBackoff == null || initialRetryBackoff.isNegative()) {
            throw new IllegalArgumentException("retry backoff cannot be negative");
        }
        if (shutdownWait == null || shutdownWait.isNegative()) {
            throw new IllegalArgumentException("shutdown wait cannot be negative");
        }
        this.maximumAttempts = maximumAttempts;
        this.initialRetryBackoff = initialRetryBackoff;
        this.shutdownWait = shutdownWait;
        this.executor = new ThreadPoolExecutor(
                writerThreads,
                writerThreads,
                0,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                new HistoryThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy()
        );
        this.executor.prestartAllCoreThreads();
    }

    @Override
    public HistoryPublishResult publish(CompletedHandArchive archive) {
        Objects.requireNonNull(archive, "completed hand archive");
        if (closed.get()) return HistoryPublishResult.CLOSED;
        try {
            executor.execute(() -> persistWithRetry(archive));
            return HistoryPublishResult.ACCEPTED;
        } catch (RejectedExecutionException rejected) {
            if (closed.get()) return HistoryPublishResult.CLOSED;
            log.error("HAND_HISTORY_QUEUE_FULL gameId={} handId={} queueCapacity={}",
                    archive.gameId(), archive.handId(), executor.getQueue().remainingCapacity() + executor.getQueue().size());
            return HistoryPublishResult.QUEUE_FULL;
        }
    }

    private void persistWithRetry(CompletedHandArchive archive) {
        for (int attempt = 1; attempt <= maximumAttempts; attempt++) {
            try {
                HandHistorySaveResult result = repository.save(archive);
                log.info("HAND_HISTORY_PERSISTED gameId={} handId={} result={} attempt={}",
                        archive.gameId(), archive.handId(), result, attempt);
                return;
            } catch (RuntimeException error) {
                if (attempt == maximumAttempts) {
                    log.error("HAND_HISTORY_PERSISTENCE_FAILED gameId={} handId={} attempts={}",
                            archive.gameId(), archive.handId(), attempt, error);
                    return;
                }
                log.warn("HAND_HISTORY_PERSISTENCE_RETRY gameId={} handId={} attempt={} code={}",
                        archive.gameId(), archive.handId(), attempt, error.getClass().getSimpleName());
                if (!waitBeforeRetry(attempt)) return;
            }
        }
    }

    private boolean waitBeforeRetry(int completedAttempt) {
        long multiplier = 1L << Math.min(completedAttempt - 1, 20);
        long delayMillis;
        try {
            delayMillis = Math.multiplyExact(initialRetryBackoff.toMillis(), multiplier);
        } catch (ArithmeticException overflow) {
            delayMillis = Long.MAX_VALUE;
        }
        delayMillis = Math.min(delayMillis, 2_000L);
        try {
            TimeUnit.MILLISECONDS.sleep(delayMillis);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    int queuedTasks() {
        return executor.getQueue().size();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(shutdownWait.toMillis(), TimeUnit.MILLISECONDS)) {
                int abandoned = executor.shutdownNow().size();
                log.warn("HAND_HISTORY_SHUTDOWN_TIMEOUT abandonedTasks={}", abandoned);
            }
        } catch (InterruptedException interrupted) {
            int abandoned = executor.shutdownNow().size();
            Thread.currentThread().interrupt();
            log.warn("HAND_HISTORY_SHUTDOWN_INTERRUPTED abandonedTasks={}", abandoned);
        }
    }

    private static final class HistoryThreadFactory implements ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "poker-hand-history-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
