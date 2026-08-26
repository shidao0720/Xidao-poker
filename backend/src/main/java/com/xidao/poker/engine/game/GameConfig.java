package com.xidao.poker.engine.game;

/** 房间级规则配置。第一版使用固定盲注。 */
public record GameConfig(int smallBlind, int bigBlind, int buyIn, int maxPlayers) {
    public GameConfig {
        if (smallBlind <= 0) throw new IllegalArgumentException("small blind must be positive");
        if (bigBlind <= smallBlind) throw new IllegalArgumentException("big blind must exceed small blind");
        if (buyIn < bigBlind) throw new IllegalArgumentException("buy-in must cover at least one big blind");
        if (maxPlayers < 2 || maxPlayers > 10) throw new IllegalArgumentException("max players must be 2..10");
        if ((long) buyIn * maxPlayers > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("total table chips must fit in a 32-bit signed integer");
        }
    }
}
