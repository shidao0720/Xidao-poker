package com.xidao.poker.web.protocol;

public record ReadyPayload(Boolean ready) {
    public ReadyPayload {
        if (ready == null) throw new IllegalArgumentException("ready is required");
    }
}
