package com.xidao.poker.application.account;

import java.time.Instant;

public record CreateRedemptionCodeCommand(
        String code,
        String currency,
        long rewardAmount,
        Integer maxRedemptions,
        Instant validFrom,
        Instant validUntil
) { }
