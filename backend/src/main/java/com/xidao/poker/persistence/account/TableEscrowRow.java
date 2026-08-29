package com.xidao.poker.persistence.account;

import java.util.UUID;

public record TableEscrowRow(
        UUID escrowId,
        UUID accountId,
        String gameId,
        String roomId,
        long buyIn,
        Long returnedChips,
        String status
) { }
