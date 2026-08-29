package com.xidao.poker.application.account;

import java.time.Instant;

public record AuthResult(String sessionToken, Instant expiresAt, AccountProfile profile) { }
