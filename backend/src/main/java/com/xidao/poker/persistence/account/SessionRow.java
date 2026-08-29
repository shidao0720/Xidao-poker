package com.xidao.poker.persistence.account;

import java.time.Instant;
import java.util.UUID;

public record SessionRow(UUID accountId, String gameId, Instant expiresAt) { }
