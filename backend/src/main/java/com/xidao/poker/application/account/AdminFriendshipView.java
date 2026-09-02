package com.xidao.poker.application.account;

import java.time.Instant;
import java.util.UUID;

public record AdminFriendshipView(
        UUID friendshipId,
        UUID requesterAccountId,
        String requesterGameId,
        UUID addresseeAccountId,
        String addresseeGameId,
        String status,
        Instant updatedAt
) { }
