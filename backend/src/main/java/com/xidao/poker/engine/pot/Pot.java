package com.xidao.poker.engine.pot;

import java.util.List;

public record Pot(int amount, List<String> eligiblePlayerIds) {
    public Pot {
        if (amount < 0) throw new IllegalArgumentException("pot amount cannot be negative");
        eligiblePlayerIds = List.copyOf(eligiblePlayerIds);
    }
}
