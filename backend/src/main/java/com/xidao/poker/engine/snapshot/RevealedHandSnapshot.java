package com.xidao.poker.engine.snapshot;

import com.xidao.poker.engine.card.Card;
import com.xidao.poker.engine.eval.HandCategory;

import java.util.List;

/**
 * 结算阶段由服务端公开的牌力结果。
 *
 * <p>只有服务端评估器可以填写 category 和 bestCards。若牌局在五张公共牌发完前
 * 因其余玩家弃牌而结束，胜者仍会亮出两张手牌，但不存在可诚实声明的五张牌型，
 * 此时 category 为 null、bestCards 为空且 showdown 为 false。</p>
 */
public record RevealedHandSnapshot(
        String playerId,
        boolean showdown,
        HandCategory category,
        List<Card> holeCards,
        List<Card> bestCards
) {
    public RevealedHandSnapshot {
        if (playerId == null || playerId.isBlank()) {
            throw new IllegalArgumentException("player id is required");
        }
        holeCards = holeCards == null ? List.of() : List.copyOf(holeCards);
        bestCards = bestCards == null ? List.of() : List.copyOf(bestCards);
        if (holeCards.size() != 2) {
            throw new IllegalArgumentException("revealed hand must contain two hole cards");
        }
        if (category == null && !bestCards.isEmpty()) {
            throw new IllegalArgumentException("best cards require a hand category");
        }
        if (category != null && bestCards.size() != 5) {
            throw new IllegalArgumentException("evaluated hand must contain five best cards");
        }
        if (showdown && category == null) {
            throw new IllegalArgumentException("showdown result requires a hand category");
        }
    }
}
