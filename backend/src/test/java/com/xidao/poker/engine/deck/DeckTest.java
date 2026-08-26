package com.xidao.poker.engine.deck;

import com.xidao.poker.engine.card.Card;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeckTest {
    @Test
    void containsExactlyFiftyTwoDistinctCards() {
        Deck deck = new Deck(7L);

        Card[] cards = deck.draw(52);

        assertThat(cards).hasSize(52).doesNotHaveDuplicates();
        assertThat(deck.remaining()).isZero();
    }

    @Test
    void oversizedBatchDrawFailsWithoutPartiallyConsumingDeck() {
        Deck deck = new Deck(7L);

        assertThatThrownBy(() -> deck.draw(53))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not enough cards");
        assertThat(deck.remaining()).isEqualTo(52);
    }

    @Test
    void sameSeedProducesSameDealOrder() {
        Card[] first = new Deck(99L).draw(10);
        Card[] second = new Deck(99L).draw(10);

        assertThat(Arrays.asList(first)).containsExactlyElementsOf(Arrays.asList(second));
    }
}
