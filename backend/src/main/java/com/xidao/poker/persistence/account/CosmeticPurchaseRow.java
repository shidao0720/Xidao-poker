package com.xidao.poker.persistence.account;

import java.util.UUID;

public record CosmeticPurchaseRow(UUID purchaseId, String catalogKey, long crystalCost) {}
