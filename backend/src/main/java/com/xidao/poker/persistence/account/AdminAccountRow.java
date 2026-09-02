package com.xidao.poker.persistence.account;

import java.util.UUID;

public record AdminAccountRow(
        UUID accountId,
        String realName,
        String primaryGameId,
        String avatarKey,
        boolean administrator,
        long chipBalance,
        long crystalBalance,
        boolean online
) { }
