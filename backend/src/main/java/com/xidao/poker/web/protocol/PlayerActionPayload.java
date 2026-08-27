package com.xidao.poker.web.protocol;

import com.xidao.poker.engine.action.ActionType;

public record PlayerActionPayload(Long handId, Long turnId, ActionType action, Integer amount) {
    public PlayerActionPayload {
        if (handId == null || handId <= 0) throw new IllegalArgumentException("hand id is required");
        if (turnId == null || turnId <= 0) throw new IllegalArgumentException("turn id is required");
        if (action == null) throw new IllegalArgumentException("action is required");
        amount = amount == null ? 0 : amount;
    }
}
