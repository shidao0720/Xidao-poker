package com.xidao.poker.application.account;

import java.util.UUID;

public record FriendView(UUID friendshipId, String gameId, String avatarKey, boolean online) { }
