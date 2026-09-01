package com.xidao.poker.application.account;

import java.util.LinkedHashMap;
import java.util.Map;

public record CosmeticLoadout(
        String avatarFrame,
        String cardBack,
        String title,
        String buttonEffect,
        String victoryEffect,
        String profileStyle
) {
    public static final CosmeticLoadout EMPTY = new CosmeticLoadout(null, null, null, null, null, null);

    public String itemFor(CosmeticSlot slot) {
        return switch (slot) {
            case AVATAR_FRAME -> avatarFrame;
            case CARD_BACK -> cardBack;
            case TITLE -> title;
            case BUTTON_EFFECT -> buttonEffect;
            case VICTORY_EFFECT -> victoryEffect;
            case PROFILE_STYLE -> profileStyle;
        };
    }

    /** 供纯 Java 游戏引擎保存公开外观，不把账户层类型带入 Engine。 */
    public Map<String, String> publicAppearance() {
        Map<String, String> result = new LinkedHashMap<>();
        put(result, "avatarFrame", avatarFrame);
        put(result, "cardBack", cardBack);
        put(result, "title", title);
        put(result, "buttonEffect", buttonEffect);
        put(result, "victoryEffect", victoryEffect);
        return Map.copyOf(result);
    }

    private static void put(Map<String, String> target, String key, String value) {
        if (value != null && !value.isBlank()) target.put(key, value);
    }
}
