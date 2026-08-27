package com.xidao.poker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "poker.room")
public record PokerRoomProperties(
        Duration emptyTtl,
        Duration cleanupInterval
) {
    public PokerRoomProperties {
        emptyTtl = emptyTtl == null ? Duration.ofSeconds(20) : emptyTtl;
        cleanupInterval = cleanupInterval == null ? Duration.ofSeconds(1) : cleanupInterval;
        if (emptyTtl.isNegative() || emptyTtl.isZero()) {
            throw new IllegalArgumentException("empty room ttl must be positive");
        }
        if (cleanupInterval.isNegative() || cleanupInterval.isZero()) {
            throw new IllegalArgumentException("room cleanup interval must be positive");
        }
    }
}
