package com.xidao.poker.application.account;

import java.util.List;

public record MailClaimResult(MailItem mail, WalletSnapshot wallet, List<String> cosmetics) {
    public MailClaimResult { cosmetics = List.copyOf(cosmetics); }
}
