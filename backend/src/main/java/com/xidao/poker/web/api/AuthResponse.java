package com.xidao.poker.web.api;

import com.xidao.poker.application.account.AccountProfile;

import java.time.Instant;

/** Authentication response intentionally omits the raw session token; it is kept in an HttpOnly cookie. */
public record AuthResponse(Instant expiresAt, AccountProfile profile) { }
