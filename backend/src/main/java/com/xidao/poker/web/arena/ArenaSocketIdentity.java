package com.xidao.poker.web.arena;

import java.util.UUID;

public record ArenaSocketIdentity(
        String connectionId,
        String roomId,
        String playerId,
        String playerName,
        String avatarKey,
        long connectionEpoch,
        String resumeToken,
        UUID accountId
) { }
