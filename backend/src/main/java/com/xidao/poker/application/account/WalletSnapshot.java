package com.xidao.poker.application.account;

public record WalletSnapshot(long chips, long spiritCrystals) {
    public WalletSnapshot {
        if (chips < 0 || spiritCrystals < 0) throw new IllegalArgumentException("wallet balances cannot be negative");
    }
}
