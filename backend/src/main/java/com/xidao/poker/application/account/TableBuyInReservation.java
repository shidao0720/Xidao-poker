package com.xidao.poker.application.account;

public record TableBuyInReservation(boolean newlyReserved, long buyIn, WalletSnapshot wallet) { }
