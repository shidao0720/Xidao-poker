package com.xidao.poker.application.account;

import java.util.UUID;

/** Application port for moving play-money chips between an account wallet and one table seat. */
public interface TableEconomyService {
    TableBuyInReservation reserveBuyIn(
            UUID accountId, String gameId, String roomId, long buyIn, String requestId);

    WalletSnapshot settleSeat(String roomId, String gameId, long finalStack, String requestId);
}
