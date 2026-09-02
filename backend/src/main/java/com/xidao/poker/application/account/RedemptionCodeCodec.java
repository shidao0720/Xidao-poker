package com.xidao.poker.application.account;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class RedemptionCodeCodec {
    private static final Pattern CODE = Pattern.compile("[A-Z0-9_-]{4,64}");

    private RedemptionCodeCodec() { }

    public static String normalize(String value) {
        String clean = value == null ? "" : Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
                .toUpperCase(Locale.ROOT);
        if (!CODE.matcher(clean).matches()) {
            throw new AccountException(AccountErrorCode.INVALID_REDEMPTION_CODE,
                    "redemption code is invalid");
        }
        return clean;
    }

    public static String hash(String normalizedCode) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(("redemption:" + normalizedCode).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
