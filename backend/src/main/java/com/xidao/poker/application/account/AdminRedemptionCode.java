package com.xidao.poker.application.account;

import java.time.Instant;

public record AdminRedemptionCode(
        String codeHash,
        String currency,
        long rewardAmount,
        Integer maxRedemptions,
        int redeemedCount,
        Instant validFrom,
        Instant validUntil,
        boolean enabled,
        Instant createdAt
) { }
