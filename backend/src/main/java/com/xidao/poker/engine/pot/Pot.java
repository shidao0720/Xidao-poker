package com.xidao.poker.engine.pot;

import java.util.List;
import java.util.Objects;

public record Pot(int amount, List<String> eligiblePlayerIds) {
    public Pot {
        if (amount <= 0) throw new IllegalArgumentException("pot amount must be positive");
        Objects.requireNonNull(eligiblePlayerIds, "eligible players");
        if (eligiblePlayerIds.isEmpty()) throw new IllegalArgumentException("pot must have an eligible player");
        if (eligiblePlayerIds.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new IllegalArgumentException("eligible player id is required");
        }
        if (eligiblePlayerIds.stream().distinct().count() != eligiblePlayerIds.size()) {
            throw new IllegalArgumentException("eligible players cannot contain duplicates");
        }
        eligiblePlayerIds = List.copyOf(eligiblePlayerIds);
    }
}
