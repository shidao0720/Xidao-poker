package com.xidao.poker.engine.card;

/**
 * 牌面点数，value 2..14（A=14）。A 在顺子 A2345 中可当 1（wheel）。
 */
public enum Rank {
    TWO(2), THREE(3), FOUR(4), FIVE(5), SIX(6), SEVEN(7),
    EIGHT(8), NINE(9), TEN(10), JACK(11), QUEEN(12), KING(13), ACE(14);

    private final int value;

    Rank(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    public static Rank fromValue(int v) {
        for (Rank r : values()) {
            if (r.value == v) {
                return r;
            }
        }
        throw new IllegalArgumentException("invalid rank value: " + v);
    }
}
