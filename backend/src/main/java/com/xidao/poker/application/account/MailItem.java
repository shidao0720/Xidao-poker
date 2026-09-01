package com.xidao.poker.application.account;

import java.time.Instant;
import java.util.UUID;

public record MailItem(
        UUID mailId,
        String type,
        String subject,
        String body,
        long rewardChips,
        long rewardCrystals,
        String rewardSkinKey,
        Instant createdAt,
        boolean read,
        boolean claimed
) {
    public boolean hasAttachment() {
        return rewardChips > 0 || rewardCrystals > 0 || (rewardSkinKey != null && !rewardSkinKey.isBlank());
    }
}
