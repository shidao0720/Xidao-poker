package com.xidao.poker.engine.pot;

import java.util.Map;

public record PotAward(int potAmount, Map<String, Integer> winnings) {
    public PotAward {
        winnings = Map.copyOf(winnings);
    }
}
