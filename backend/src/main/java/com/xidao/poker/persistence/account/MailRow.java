package com.xidao.poker.persistence.account;

import java.time.Instant;
import java.util.UUID;

public record MailRow(UUID mailId, String type, String subject, String body,
                      long rewardChips, long rewardCrystals, String rewardSkinKey,
                      Instant createdAt, Instant readAt, Instant claimedAt) { }
