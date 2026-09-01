package com.xidao.poker.application.account;

public record RedemptionResult(
        String currency,
        long amount,
        WalletSnapshot wallet
) { }
