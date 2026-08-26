package com.xidao.poker.engine.eval;

import com.xidao.poker.engine.card.Card;

/**
 * 一手牌的评估结果：数值化 key（可直接比大小）、牌型等级、构成该牌型的最佳 5 张牌。
 */
public record HandResult(long key, HandCategory category, Card[] bestCards) {
    public HandResult {
        if (category == null) throw new IllegalArgumentException("hand category is required");
        bestCards = bestCards == null ? new Card[0] : bestCards.clone();
        if (bestCards.length != 0 && bestCards.length != 5) {
            throw new IllegalArgumentException("best cards must be empty or contain five cards");
        }
    }

    public static HandResult fromKey(long key) {
        return new HandResult(key, HandCategory.fromRank((int) (key >>> 20)), new Card[0]);
    }

    @Override
    public Card[] bestCards() {
        return bestCards.clone();
    }
}
