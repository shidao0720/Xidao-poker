package com.xidao.poker.web.protocol;

public record StartGamePayload(Long expectedPreviousHandId) {
    public StartGamePayload {
        if (expectedPreviousHandId == null || expectedPreviousHandId < 0) {
            throw new IllegalArgumentException("expected previous hand id is required");
        }
    }
}
