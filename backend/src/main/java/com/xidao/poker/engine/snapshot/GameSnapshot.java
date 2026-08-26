package com.xidao.poker.engine.snapshot;

import com.xidao.poker.engine.action.ActionType;
import com.xidao.poker.engine.card.Card;
import com.xidao.poker.engine.game.GamePhase;
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
        int currentBet,
        int minimumRaise,
        int pot,
        List<Card> communityCards,
        List<PlayerSnapshot> players,
        Set<ActionType> legalActions,
        List<PotAward> awards,
        long lastSequence
) {
    public GameSnapshot {
        communityCards = communityCards == null ? List.of() : List.copyOf(communityCards);
        players = players == null ? List.of() : List.copyOf(players);
        legalActions = legalActions == null ? Set.of() : Set.copyOf(legalActions);
        awards = awards == null ? List.of() : List.copyOf(awards);
    }
}
