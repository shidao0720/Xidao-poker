package com.xidao.poker.application.account;

import java.util.List;

/** 仅包含可公开给账户本人的字段，绝不包含真实姓名或密码哈希。 */
public record AccountProfile(String gameId, List<String> gameIds, WalletSnapshot wallet, String avatarKey) {
    public AccountProfile(String gameId, List<String> gameIds, WalletSnapshot wallet) {
        this(gameId, gameIds, wallet, AvatarCatalog.DEFAULT);
    }

    public AccountProfile {
        gameIds = List.copyOf(gameIds);
        avatarKey = AvatarCatalog.normalize(avatarKey);
    }
}
