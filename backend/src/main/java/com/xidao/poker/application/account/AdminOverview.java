package com.xidao.poker.application.account;

public record AdminOverview(
        long accounts,
        long administrators,
        long enabledRedemptionCodes,
        long redemptionClaims,
        long mailMessages,
        long deliveredMail,
        long grantedCosmetics
) { }
