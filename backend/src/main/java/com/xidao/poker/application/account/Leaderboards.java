package com.xidao.poker.application.account;

import java.util.List;

public record Leaderboards(
        List<LeaderboardEntry> mostHandsWon,
        List<LeaderboardEntry> mostTotalWinnings,
        List<LeaderboardEntry> largestSingleHandGain
) {
    public Leaderboards {
        mostHandsWon = List.copyOf(mostHandsWon);
        mostTotalWinnings = List.copyOf(mostTotalWinnings);
        largestSingleHandGain = List.copyOf(largestSingleHandGain);
    }
}
