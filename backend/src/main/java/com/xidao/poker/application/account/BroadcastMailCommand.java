package com.xidao.poker.application.account;

public record BroadcastMailCommand(
        String type,
        String subject,
        String body,
        long rewardChips,
        long rewardCrystals,
        String rewardSkinKey
) { }
