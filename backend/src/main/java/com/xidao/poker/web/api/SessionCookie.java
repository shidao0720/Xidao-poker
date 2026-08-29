package com.xidao.poker.web.api;

import com.xidao.poker.application.account.AccountException;
import com.xidao.poker.application.account.AccountErrorCode;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;

import java.time.Duration;
import java.time.Instant;

/** Same-origin LAN session cookie. The token is never placed in URLs or application logs. */
public final class SessionCookie {
    public static final String NAME = "xidao_session";

    private SessionCookie() { }

    public static String require(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (NAME.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                    return cookie.getValue();
                }
            }
        }
        throw new AccountException(AccountErrorCode.UNAUTHORIZED, "session is required");
    }

    public static String optional(HttpServletRequest request) {
        try {
            return require(request);
        } catch (AccountException ignored) {
            return null;
        }
    }

    public static ResponseCookie active(String token, Instant expiresAt, Instant now) {
        Duration maxAge = Duration.between(now, expiresAt);
        return ResponseCookie.from(NAME, token)
                .httpOnly(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge)
                .build();
    }

    public static ResponseCookie expired() {
        return ResponseCookie.from(NAME, "")
                .httpOnly(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
    }
}
