package com.xidao.poker.application.account;

import java.util.UUID;

public interface AccountService {
    AuthResult register(String realName, String gameId, String password);
    AuthResult login(String realName, String gameId, String password);
    void logout(String sessionToken);
    AccountPrincipal authenticate(String sessionToken);
    AccountProfile profile(String sessionToken);
    AccountProfile updateAvatar(String sessionToken, String avatarKey);
    CheckInResult checkIn(String sessionToken, String requestId);
    WalletSnapshot exchangeForCrystals(String sessionToken, String requestId, long chips);
    RedemptionResult redeemCode(String sessionToken, String requestId, String code);
    TableSessionResult tableSessionResult(String sessionToken, String roomId);
    MailInbox inbox(String sessionToken);
    MailItem markMailRead(String sessionToken, UUID mailId);
    MailClaimResult claimMail(String sessionToken, UUID mailId, String requestId);
    MailItem broadcastMail(String sessionToken, String requestId, BroadcastMailCommand command);
    java.util.List<StoreItem> storeCatalog(String sessionToken);
    StorePurchaseResult purchaseCosmetic(String sessionToken, String requestId, String itemKey);
    AccountProfile equipCosmetic(String sessionToken, String requestId, CosmeticSlot slot, String itemKey);
    Leaderboards leaderboards();
}
