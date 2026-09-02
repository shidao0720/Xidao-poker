package com.xidao.poker.persistence.account;

import java.time.Instant;

public record RedemptionCodeRow(
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
