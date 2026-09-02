package com.xidao.poker.application.account;

import java.util.UUID;

public record AdminWalletAdjustment(UUID accountId, WalletSnapshot wallet) { }
