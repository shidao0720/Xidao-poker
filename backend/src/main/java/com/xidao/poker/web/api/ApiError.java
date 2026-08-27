package com.xidao.poker.web.api;

import java.time.Instant;

public record ApiError(String code, String message, Instant timestamp) {
    public ApiError {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("error code is required");
        if (message == null || message.isBlank()) throw new IllegalArgumentException("error message is required");
        if (timestamp == null) throw new IllegalArgumentException("error timestamp is required");
    }
}
