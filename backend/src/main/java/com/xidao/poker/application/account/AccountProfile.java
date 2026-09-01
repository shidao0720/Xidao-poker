package com.xidao.poker.application.account;

import java.util.List;

/** 仅包含可公开给账户本人的字段，绝不包含真实姓名或密码哈希。 */
public record AccountProfile(String gameId, List<String> gameIds, WalletSnapshot wallet,
                             String avatarKey, boolean admin, List<String> cosmetics,
                             CosmeticLoadout loadout) {
    public AccountProfile(String gameId, List<String> gameIds, WalletSnapshot wallet) {
        this(gameId, gameIds, wallet, AvatarCatalog.DEFAULT, false, List.of(), CosmeticLoadout.EMPTY);
    }

    public AccountProfile(String gameId, List<String> gameIds, WalletSnapshot wallet, String avatarKey) {
        this(gameId, gameIds, wallet, avatarKey, false, List.of(), CosmeticLoadout.EMPTY);
    }

    public AccountProfile {
        gameIds = List.copyOf(gameIds);
        avatarKey = AvatarCatalog.normalize(avatarKey);
        cosmetics = cosmetics == null ? List.of() : List.copyOf(cosmetics);
        loadout = loadout == null ? CosmeticLoadout.EMPTY : loadout;
    }
}
