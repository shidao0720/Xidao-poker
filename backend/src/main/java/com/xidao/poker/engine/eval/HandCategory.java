package com.xidao.poker.engine.eval;

/**
 * 牌型等级，rank 越大越强。皇家同花顺不单列——它就是 STRAIGHT_FLUSH 且 high=A。
 */
public enum HandCategory {
    HIGH_CARD(0),
    ONE_PAIR(1),
    TWO_PAIR(2),
    THREE_OF_A_KIND(3),
    STRAIGHT(4),
    FLUSH(5),
    FULL_HOUSE(6),
    FOUR_OF_A_KIND(7),
    STRAIGHT_FLUSH(8);

    public final int rank;

    HandCategory(int rank) {
        this.rank = rank;
    }

    public static HandCategory fromRank(int rank) {
        for (HandCategory c : values()) {
            if (c.rank == rank) {
                return c;
            }
        }
        throw new IllegalArgumentException("invalid category rank: " + rank);
    }
}
