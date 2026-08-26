package com.xidao.poker.engine.game;

/** 整场游戏及单手牌共用的显式状态机阶段。 */
public enum GamePhase {
    WAITING,
    READY,
    DEALING,
    PREFLOP,
    FLOP,
    TURN,
    RIVER,
    SHOWDOWN,
    SETTLEMENT,
    ROUND_END;

    public boolean isBettingPhase() {
        return this == PREFLOP || this == FLOP || this == TURN || this == RIVER;
    }
}
