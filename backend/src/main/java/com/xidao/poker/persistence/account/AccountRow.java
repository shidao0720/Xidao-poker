package com.xidao.poker.persistence.account;

import java.util.UUID;

public record AccountRow(
        UUID accountId,
        String realNameKey,
        String passwordHash,
        String primaryGameId,
        String avatarKey,
        boolean admin,
        long chipBalance,
        long crystalBalance
) { }
