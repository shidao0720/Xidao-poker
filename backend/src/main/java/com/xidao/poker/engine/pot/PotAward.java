package com.xidao.poker.engine.pot;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record PotAward(int potAmount, Map<String, Integer> winnings) {
    public PotAward {
        if (potAmount <= 0) throw new IllegalArgumentException("pot amount must be positive");
        Objects.requireNonNull(winnings, "winnings");
        if (winnings.isEmpty()) throw new IllegalArgumentException("pot award must have a winner");
        int distributed = 0;
        for (Map.Entry<String, Integer> entry : winnings.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw new IllegalArgumentException("winner id is required");
            }
            if (entry.getValue() == null || entry.getValue() <= 0) {
                throw new IllegalArgumentException("winning amount must be positive");
            }
            distributed = Math.addExact(distributed, entry.getValue());
        }
        if (distributed != potAmount) throw new IllegalArgumentException("pot award must distribute the full pot");
        winnings = Collections.unmodifiableMap(new LinkedHashMap<>(winnings));
    }
}
