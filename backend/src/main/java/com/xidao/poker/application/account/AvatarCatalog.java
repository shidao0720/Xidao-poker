package com.xidao.poker.application.account;

import java.util.Set;

/** 头像只允许使用前端随应用发布的受控资源。 */
public final class AvatarCatalog {
    public static final String DEFAULT = "default";
    private static final Set<String> ALLOWED = Set.of(
            DEFAULT, "azure", "crimson", "violet", "moon", "prism",
            "bobo", "god", "lian", "niu", "yun", "zhen", "zhi",
            "dna", "li", "lu", "wu", "zhan"
    );

    private AvatarCatalog() { }

    public static String normalize(String value) {
        String normalized = value == null ? DEFAULT : value.trim().toLowerCase(java.util.Locale.ROOT);
        if (!ALLOWED.contains(normalized)) throw new IllegalArgumentException("avatar is invalid");
        return normalized;
    }

    public static boolean isAllowed(String value) {
        return value != null && ALLOWED.contains(value.trim().toLowerCase(java.util.Locale.ROOT));
    }
}
