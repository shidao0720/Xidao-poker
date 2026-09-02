package com.xidao.poker.persistence.account;

import java.time.Instant;
import java.util.UUID;

public record FriendshipViewRow(
        UUID friendshipId,
        UUID requesterId,
        String requesterGameId,
        String requesterAvatarKey,
        boolean requesterOnline,
        UUID addresseeId,
        String addresseeGameId,
        String addresseeAvatarKey,
        boolean addresseeOnline,
        String status,
        Instant updatedAt
) { }
