package com.xidao.poker.application.account;

import java.util.List;
import java.util.UUID;

public record AdminAccountView(
        UUID accountId,
        String realName,
        String primaryGameId,
        List<String> gameIds,
        String avatarKey,
        boolean administrator,
        WalletSnapshot wallet,
        boolean online,
        String passwordStatus
) {
    public AdminAccountView {
        gameIds = List.copyOf(gameIds);
    }
}
