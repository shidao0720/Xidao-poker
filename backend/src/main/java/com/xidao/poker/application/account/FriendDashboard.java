package com.xidao.poker.application.account;

import java.util.List;

public record FriendDashboard(
        List<FriendView> friends,
        List<FriendView> incomingRequests,
        List<FriendView> outgoingRequests
) {
    public FriendDashboard {
        friends = List.copyOf(friends);
        incomingRequests = List.copyOf(incomingRequests);
        outgoingRequests = List.copyOf(outgoingRequests);
    }
}
