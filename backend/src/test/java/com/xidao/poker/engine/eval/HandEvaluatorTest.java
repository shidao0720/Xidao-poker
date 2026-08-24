package com.xidao.poker.engine.eval;

import com.xidao.poker.engine.card.Card;
import com.xidao.poker.engine.card.Rank;
import com.xidao.poker.engine.card.Suit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HandEvaluatorTest {

    private static Card c(String s) {
        String rankStr = s.substring(0, s.length() - 1);
        char suitChar = s.charAt(s.length() - 1);
        Rank rank = switch (rankStr) {
            case "A" -> Rank.ACE;
            case "K" -> Rank.KING;
            case "Q" -> Rank.QUEEN;
            case "J" -> Rank.JACK;
            case "T" -> Rank.TEN;
            default -> Rank.fromValue(Integer.parseInt(rankStr));
        };
        Suit suit = switch (suitChar) {
            case 'S' -> Suit.SPADES;
            case 'H' -> Suit.HEARTS;
            case 'D' -> Suit.DIAMONDS;
            case 'C' -> Suit.CLUBS;
            default -> throw new IllegalArgumentException("bad suit " + suitChar);
        };
        return new Card(rank, suit);
    }

    private static long eval(String... cards) {
        Card[] arr = new Card[cards.length];
        for (int i = 0; i < cards.length; i++) {
            arr[i] = c(cards[i]);
        }
        return HandEvaluator.evaluate5(arr);
    }

    private static HandCategory cat(String... cards) {
        return HandEvaluator.categoryOf(eval(cards));
    }

    @Test
    void recognizesRoyalFlush() {
        assertThat(cat("AS", "KS", "QS", "JS", "TS")).isEqualTo(HandCategory.STRAIGHT_FLUSH);
    }

    @Test
    void recognizesStraightFlush() {
        assertThat(cat("9H", "8H", "7H", "6H", "5H")).isEqualTo(HandCategory.STRAIGHT_FLUSH);
    }

    @Test
    void recognizesFourOfAKind() {
        assertThat(cat("9S", "9H", "9D", "9C", "KD")).isEqualTo(HandCategory.FOUR_OF_A_KIND);
    }

    @Test
    void recognizesFullHouse() {
        assertThat(cat("AS", "AH", "AD", "KC", "KS")).isEqualTo(HandCategory.FULL_HOUSE);
    }

    @Test
    void recognizesFlush() {
        assertThat(cat("AS", "JS", "8S", "4S", "2S")).isEqualTo(HandCategory.FLUSH);
    }

    @Test
    void recognizesStraight() {
        assertThat(cat("9S", "8H", "7D", "6C", "5S")).isEqualTo(HandCategory.STRAIGHT);
    }

    @Test
    void recognizesThreeOfAKind() {
        assertThat(cat("9S", "9H", "9D", "KC", "2S")).isEqualTo(HandCategory.THREE_OF_A_KIND);
    }

    @Test
    void recognizesTwoPair() {
        assertThat(cat("9S", "9H", "4D", "4C", "2S")).isEqualTo(HandCategory.TWO_PAIR);
    }

    @Test
    void recognizesOnePair() {
        assertThat(cat("9S", "9H", "KD", "4C", "2S")).isEqualTo(HandCategory.ONE_PAIR);
    }

    @Test
    void recognizesHighCard() {
        assertThat(cat("AS", "9H", "7D", "4C", "2S")).isEqualTo(HandCategory.HIGH_CARD);
    }

    @Test
    void wheelStraightIsStraight() {
        // A-2-3-4-5，A 当 1
        assertThat(cat("AS", "2H", "3D", "4C", "5S")).isEqualTo(HandCategory.STRAIGHT);
    }

    @Test
    void wheelLosesToSixHighStraight() {
        long wheel = eval("AS", "2H", "3D", "4C", "5S");
        long six = eval("6S", "2H", "3D", "4C", "5S");
        assertThat(six).isGreaterThan(wheel);
    }

    @Test
    void broadwayBeatsWheel() {
        long broadway = eval("AS", "KH", "QD", "JC", "TS");
        long wheel = eval("AS", "2H", "3D", "4C", "5S");
        assertThat(broadway).isGreaterThan(wheel);
    }

    @Test
    void kickerBreaksTie() {
        // 同为 AA pair，比较踢脚
        long high = eval("AS", "AH", "KD", "QC", "TS");
        long low = eval("AS", "AH", "KD", "QC", "9S");
        assertThat(high).isGreaterThan(low);
    }

    @Test
    void equalHandsTie() {
        long a = eval("AS", "AH", "KD", "QC", "TS");
        long b = eval("AC", "AD", "KS", "QH", "TD");
        assertThat(a).isEqualTo(b);
    }

    @Test
    void evaluate7PicksBestFive() {
        // 手牌 A2，公共牌 A A K Q J：应识别三条 A 而非两对
        Card[] seven = {
                c("AS"), c("2H"),      // 手牌
                c("AD"), c("AH"), c("KD"), c("QD"), c("JD")  // 公共牌
        };
        long key = HandEvaluator.evaluate7(seven);
        assertThat(HandEvaluator.categoryOf(key)).isEqualTo(HandCategory.THREE_OF_A_KIND);
    }

    @Test
    void evaluate7UsesCommunityFlush() {
        // 手牌与公共牌组合出同花
        Card[] seven = {
                c("AS"), c("KH"),
                c("3S"), c("7S"), c("9S"), c("JD"), c("2S")
        };
        assertThat(HandEvaluator.categoryOf(HandEvaluator.evaluate7(seven)))
                .isEqualTo(HandCategory.FLUSH);
    }

    @Test
    void bestHandReturnsBestFive() {
        HandResult r = HandEvaluator.bestHand(
                new Card[]{c("AS"), c("2H")},
                new Card[]{c("AD"), c("AH"), c("KD"), c("QD"), c("JD")});
        assertThat(r.category()).isEqualTo(HandCategory.THREE_OF_A_KIND);
        assertThat(r.bestCards()).hasSize(5);
    }
}
