package com.xidao.poker.application.account;

import java.util.Locale;

public enum CosmeticSlot {
    AVATAR_FRAME,
    CARD_BACK,
    TITLE,
    BUTTON_EFFECT,
    VICTORY_EFFECT,
    PROFILE_STYLE;

    public static CosmeticSlot fromApi(String value) {
        if (value == null) throw new AccountException(AccountErrorCode.INVALID_COSMETIC, "cosmetic slot is required");
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new AccountException(AccountErrorCode.INVALID_COSMETIC, "cosmetic slot is invalid");
        }
    }
}
