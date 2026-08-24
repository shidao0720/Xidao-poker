package com.xidao.poker.engine.deck;

import com.xidao.poker.engine.card.Card;
import com.xidao.poker.engine.card.Rank;
import com.xidao.poker.engine.card.Suit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 52 张牌的牌组。可注入随机种子保证确定性（便于测试与回放）。
 * 烧牌（burn）通过 {@link #draw()} 丢弃一张实现。
 */
public final class Deck {

    private final List<Card> cards;
    private int index = 0;

    public Deck(long seed) {
        this.cards = build();
        Collections.shuffle(this.cards, new Random(seed));
    }

    public Deck(Random rng) {
        this.cards = build();
        Collections.shuffle(this.cards, rng);
    }

    private static List<Card> build() {
        List<Card> list = new ArrayList<>(52);
        for (Suit s : Suit.values()) {
            for (Rank r : Rank.values()) {
                list.add(new Card(r, s));
            }
        }
        return list;
    }

    /** 抽一张牌（也用于烧牌）。 */
    public Card draw() {
        if (index >= cards.size()) {
            throw new IllegalStateException("deck is empty");
        }
        return cards.get(index++);
    }

    /** 连续抽 n 张。 */
    public Card[] draw(int n) {
        Card[] out = new Card[n];
        for (int i = 0; i < n; i++) {
            out[i] = draw();
        }
        return out;
    }

    public int remaining() {
        return cards.size() - index;
    }
}
