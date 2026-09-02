package com.xidao.poker.persistence.account;

import java.util.UUID;

public record FriendshipStateRow(UUID friendshipId, UUID requesterId, UUID addresseeId, String status) { }
