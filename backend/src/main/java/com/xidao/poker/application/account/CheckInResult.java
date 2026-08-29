package com.xidao.poker.application.account;

import java.time.LocalDate;

public record CheckInResult(boolean awarded, long awardedChips, LocalDate date, WalletSnapshot wallet) { }
