package com.xidao.poker.engine.player;

/** 玩家在房间和牌局中的唯一生命周期状态。 */
public enum PlayerStatus {
    ACTIVE,
    FOLDED,
    ALL_IN,
    DISCONNECTED,
    SPECTATOR,
    BUSTED
}
