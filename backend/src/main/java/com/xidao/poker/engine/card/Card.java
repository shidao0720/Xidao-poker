package com.xidao.poker.engine.card;

import java.util.Objects;

/**
 * 一张扑克牌。不可变，适合作为牌组/公共牌/手牌的共享元素。
 */
public record Card(Rank rank, Suit suit) {
    public Card {
        Objects.requireNonNull(rank, "card rank");
        Objects.requireNonNull(suit, "card suit");
    }

    @Override
    public String toString() {
        return rank + " of " + suit;
    }
}
