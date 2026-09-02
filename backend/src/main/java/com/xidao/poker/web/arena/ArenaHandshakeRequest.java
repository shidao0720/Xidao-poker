package com.xidao.poker.web.arena;

import java.util.UUID;

public record ArenaHandshakeRequest(
        String roomId,
        String playerId,
        String playerName,
        String avatarKey,
        String resumeToken,
        int protocolVersion,
        String buildVersion,
        UUID accountId
) { }
