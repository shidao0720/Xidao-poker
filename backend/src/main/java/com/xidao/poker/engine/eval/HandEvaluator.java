package com.xidao.poker.engine.eval;

import com.xidao.poker.engine.card.Card;
import com.xidao.poker.engine.card.Suit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 手牌评估器：把任意 5 张牌数值化为一个可比较大小的 {@code long} key，
 * 并从 7 张（2 手牌 + 5 公共牌）中选出最佳 5 张组合。
 *
 * <p>key 结构：高 4 bit 存牌型等级（{@link HandCategory#rank}），低位用若干 4-bit nibble
 * 依次存 tiebreak 牌值（从高到低）。因此两个 key 直接比大小即可分出胜负，相等即平局。</p>
 */
public final class HandEvaluator {

    private HandEvaluator() {
    }

    /** 评估 5 张牌，返回数值化 key。 */
    public static long evaluate5(Card[] cards) {
        if (cards == null || cards.length != 5) {
            throw new IllegalArgumentException("exactly 5 cards required");
        }

        // rank 降序
        int[] r = new int[5];
        for (int i = 0; i < 5; i++) {
            r[i] = cards[i].rank().value();
        }
        Arrays.sort(r);
        int[] rd = new int[5];
        for (int i = 0; i < 5; i++) {
            rd[i] = r[4 - i];
        }

        boolean flush = true;
        Suit s0 = cards[0].suit();
        for (int i = 1; i < 5; i++) {
            if (cards[i].suit() != s0) {
                flush = false;
                break;
            }
        }

        int[] cnt = new int[15];
        for (int v : rd) {
            cnt[v]++;
        }

        int straightHigh = straightHigh(rd);

        int four = 0;
        int trips = 0;
        List<Integer> pairs = new ArrayList<>();
        for (int v = 14; v >= 2; v--) {
            if (cnt[v] == 4) {
                four = v;
            } else if (cnt[v] == 3) {
                trips = v;
            } else if (cnt[v] == 2) {
                pairs.add(v);
            }
        }

        List<Integer> kickers = new ArrayList<>();
        for (int v = 14; v >= 2; v--) {
            if (cnt[v] == 1) {
                kickers.add(v);
            }
        }

        if (flush && straightHigh > 0) {
            return key(HandCategory.STRAIGHT_FLUSH, straightHigh);
        }
        if (four > 0) {
            return key(HandCategory.FOUR_OF_A_KIND, four, kickers.get(0));
        }
        if (trips > 0 && !pairs.isEmpty()) {
            return key(HandCategory.FULL_HOUSE, trips, pairs.get(0));
        }
        if (flush) {
            return key(HandCategory.FLUSH, rd[0], rd[1], rd[2], rd[3], rd[4]);
        }
        if (straightHigh > 0) {
            return key(HandCategory.STRAIGHT, straightHigh);
        }
        if (trips > 0) {
            return key(HandCategory.THREE_OF_A_KIND, trips, kickers.get(0), kickers.get(1));
        }
        if (pairs.size() >= 2) {
            return key(HandCategory.TWO_PAIR, pairs.get(0), pairs.get(1), kickers.get(0));
        }
        if (pairs.size() == 1) {
            return key(HandCategory.ONE_PAIR, pairs.get(0), kickers.get(0), kickers.get(1), kickers.get(2));
        }
        return key(HandCategory.HIGH_CARD, rd[0], rd[1], rd[2], rd[3], rd[4]);
    }

    /** 从 7 张牌中选出最佳 5 张，返回数值化 key。 */
    public static long evaluate7(Card[] seven) {
        if (seven == null || seven.length != 7) {
            throw new IllegalArgumentException("exactly 7 cards required");
        }
        long best = Long.MIN_VALUE;
        Card[] five = new Card[5];
        for (int mask = 0; mask < (1 << 7); mask++) {
            if (Integer.bitCount(mask) != 5) {
                continue;
            }
            int idx = 0;
            for (int i = 0; i < 7; i++) {
                if ((mask & (1 << i)) != 0) {
                    five[idx++] = seven[i];
                }
            }
            long k = evaluate5(five);
            if (k > best) {
                best = k;
            }
        }
        return best;
    }

    /**
     * 评估「2 手牌 + 5 公共牌」，返回最佳 5 张组合及牌型。
     */
    public static HandResult bestHand(Card[] hole, Card[] community) {
        if (hole == null || hole.length != 2) {
            throw new IllegalArgumentException("exactly 2 hole cards required");
        }
        if (community == null || community.length != 5) {
            throw new IllegalArgumentException("exactly 5 community cards required");
        }
        Card[] seven = new Card[7];
        System.arraycopy(hole, 0, seven, 0, 2);
        System.arraycopy(community, 0, seven, 2, 5);

        long best = Long.MIN_VALUE;
        int bestMask = 0;
        Card[] five = new Card[5];
        for (int mask = 0; mask < (1 << 7); mask++) {
            if (Integer.bitCount(mask) != 5) {
                continue;
            }
            int idx = 0;
            for (int i = 0; i < 7; i++) {
                if ((mask & (1 << i)) != 0) {
                    five[idx++] = seven[i];
                }
            }
            long k = evaluate5(five);
            if (k > best) {
                best = k;
                bestMask = mask;
            }
        }

        Card[] bestCards = new Card[5];
        int idx = 0;
        for (int i = 0; i < 7; i++) {
            if ((bestMask & (1 << i)) != 0) {
                bestCards[idx++] = seven[i];
            }
        }
        return new HandResult(best, categoryOf(best), bestCards);
    }

    /** 从 key 中还原牌型等级。 */
    public static HandCategory categoryOf(long key) {
        return HandCategory.fromRank((int) (key >>> 20));
    }

    private static int straightHigh(int[] rd) {
        boolean consecutive = true;
        for (int i = 0; i < 4; i++) {
            if (rd[i] - rd[i + 1] != 1) {
                consecutive = false;
                break;
            }
        }
        if (consecutive) {
            return rd[0];
        }
        // wheel: A-5-4-3-2，A 当 1，high = 5
        if (rd[0] == 14 && rd[1] == 5 && rd[2] == 4 && rd[3] == 3 && rd[4] == 2) {
            return 5;
        }
        return 0;
    }

    private static long key(HandCategory c, int... tiebreak) {
        long tie = 0;
        for (int t : tiebreak) {
            tie = (tie << 4) | (t & 0xF);
        }
        return ((long) c.rank << 20) | tie;
    }
}
