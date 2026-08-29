package com.xidao.poker.engine.snapshot;

import com.xidao.poker.engine.action.ActionType;
import com.xidao.poker.engine.card.Card;
import com.xidao.poker.engine.game.GamePhase;
import com.xidao.poker.engine.pot.Pot;
import com.xidao.poker.engine.pot.PotAward;

import java.util.List;
import java.util.Set;

/** 面向单个查看者过滤后的完整状态；客户端收到后必须整体替换旧状态。 */
public record GameSnapshot(
        String sessionId,
        String ownerId,
        long handId,
        GamePhase phase,
        Integer buttonSeat,
        Integer smallBlindSeat,
        Integer bigBlindSeat,
        Integer currentActorSeat,
        long turnId,
        int currentBet,
        int minimumRaise,
        int pot,
        List<Pot> pots,
        List<Card> communityCards,
        List<PlayerSnapshot> players,
        ActionOptions actionOptions,
        List<PotAward> awards,
        List<RevealedHandSnapshot> revealedHands,
        long lastSequence
) {
    public GameSnapshot {
        pots = pots == null ? List.of() : List.copyOf(pots);
        communityCards = communityCards == null ? List.of() : List.copyOf(communityCards);
        players = players == null ? List.of() : List.copyOf(players);
        actionOptions = actionOptions == null ? ActionOptions.none() : actionOptions;
        awards = awards == null ? List.of() : List.copyOf(awards);
        revealedHands = revealedHands == null ? List.of() : List.copyOf(revealedHands);
    }

    /** 保留便利访问器，调用方无需从 ActionOptions 再取一次集合。 */
    public Set<ActionType> legalActions() {
        return actionOptions.legalActions();
    }
}
