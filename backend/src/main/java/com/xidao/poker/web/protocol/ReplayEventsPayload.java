package com.xidao.poker.web.protocol;

public record ReplayEventsPayload(Long afterSequence) {
    public ReplayEventsPayload {
        if (afterSequence == null || afterSequence < 0) {
            throw new IllegalArgumentException("after sequence is required");
        }
    }
}
