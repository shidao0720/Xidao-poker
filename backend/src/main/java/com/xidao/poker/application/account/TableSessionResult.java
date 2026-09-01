package com.xidao.poker.application.account;

public record TableSessionResult(
        String status,
        long buyIn,
        Long returnedChips,
        Long netChips
) {
    public static TableSessionResult active(long buyIn) {
        return new TableSessionResult("ACTIVE", buyIn, null, null);
    }

    public static TableSessionResult settled(long buyIn, long returnedChips) {
        return new TableSessionResult("SETTLED", buyIn, returnedChips, returnedChips - buyIn);
    }
}
