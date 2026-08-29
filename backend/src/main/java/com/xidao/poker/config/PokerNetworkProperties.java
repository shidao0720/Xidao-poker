package com.xidao.poker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "poker.network")
public record PokerNetworkProperties(
        Duration disconnectGrace,
        Duration turnTimeout,
        int maximumTextMessageBytes,
        int sendTimeLimitMillis,
        int sendBufferBytes,
        List<String> allowedOriginPatterns
) {
    public PokerNetworkProperties {
        disconnectGrace = disconnectGrace == null ? Duration.ofSeconds(30) : disconnectGrace;
        turnTimeout = turnTimeout == null ? Duration.ofSeconds(30) : turnTimeout;
        maximumTextMessageBytes = maximumTextMessageBytes == 0 ? 16 * 1024 : maximumTextMessageBytes;
        sendTimeLimitMillis = sendTimeLimitMillis == 0 ? 10_000 : sendTimeLimitMillis;
        sendBufferBytes = sendBufferBytes == 0 ? 1024 * 1024 : sendBufferBytes;
        allowedOriginPatterns = allowedOriginPatterns == null ? List.of() : List.copyOf(allowedOriginPatterns);

        if (disconnectGrace.isNegative() || disconnectGrace.isZero()) {
            throw new IllegalArgumentException("disconnect grace must be positive");
        }
        if (turnTimeout.isNegative() || turnTimeout.isZero()) {
            throw new IllegalArgumentException("turn timeout must be positive");
        }
        if (maximumTextMessageBytes <= 0 || sendTimeLimitMillis <= 0 || sendBufferBytes <= 0) {
            throw new IllegalArgumentException("network limits must be positive");
        }
    }

}
