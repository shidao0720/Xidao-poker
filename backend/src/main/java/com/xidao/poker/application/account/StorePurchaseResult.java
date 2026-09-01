package com.xidao.poker.application.account;

import java.util.List;

public record StorePurchaseResult(
        StoreItem item,
        WalletSnapshot wallet,
        List<String> cosmetics,
        CosmeticLoadout loadout
) {
    public StorePurchaseResult {
        cosmetics = List.copyOf(cosmetics);
    }
}
