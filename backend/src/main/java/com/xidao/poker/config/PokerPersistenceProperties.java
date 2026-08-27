package com.xidao.poker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("poker.persistence")
public record PokerPersistenceProperties(
        boolean enabled,
        int writerThreads,
        int queueCapacity,
        int maximumAttempts,
        Duration initialRetryBackoff,
        Duration shutdownWait,
        Database database
) {
    public PokerPersistenceProperties {
        if (writerThreads <= 0) throw new IllegalArgumentException("history writer threads must be positive");
        if (queueCapacity <= 0) throw new IllegalArgumentException("history queue capacity must be positive");
        if (maximumAttempts <= 0) throw new IllegalArgumentException("history maximum attempts must be positive");
        if (initialRetryBackoff == null || initialRetryBackoff.isNegative()) {
            throw new IllegalArgumentException("history retry backoff cannot be negative");
        }
        if (shutdownWait == null || shutdownWait.isNegative()) {
            throw new IllegalArgumentException("history shutdown wait cannot be negative");
        }
        if (enabled) {
            if (database == null) throw new IllegalArgumentException("history database configuration is required");
            database.validate();
        }
    }

    public record Database(
            String jdbcUrl,
            String username,
            String password,
            int maximumPoolSize
    ) {
        private void validate() {
            if (jdbcUrl == null || jdbcUrl.isBlank()) throw new IllegalArgumentException("database JDBC URL is required");
            if (username == null || username.isBlank()) throw new IllegalArgumentException("database username is required");
            if (maximumPoolSize <= 0) throw new IllegalArgumentException("database pool size must be positive");
        }
    }
}
