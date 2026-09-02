package com.xidao.poker.application.account;

import java.util.UUID;

public interface AccountService {
    AuthResult register(String realName, String gameId, String password);
    AuthResult login(String realName, String gameId, String password);
    void logout(String sessionToken);
    void heartbeat(String sessionToken, String requestId);
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
    AdminOverview adminOverview(String sessionToken);
    java.util.List<AdminRedemptionCode> adminRedemptionCodes(String sessionToken);
    AdminRedemptionCodeCreated createRedemptionCode(String sessionToken, String requestId,
                                                     CreateRedemptionCodeCommand command);
    AdminRedemptionCode setRedemptionCodeEnabled(String sessionToken, String requestId,
                                                 String codeHash, boolean enabled);
    java.util.List<StoreItem> storeCatalog(String sessionToken);
    StorePurchaseResult purchaseCosmetic(String sessionToken, String requestId, String itemKey);
    AccountProfile equipCosmetic(String sessionToken, String requestId, CosmeticSlot slot, String itemKey);
    FriendDashboard friends(String sessionToken);
    FriendDashboard sendFriendRequest(String sessionToken, String requestId, String gameId);
    FriendDashboard acceptFriendRequest(String sessionToken, String requestId, UUID friendshipId);
    FriendDashboard rejectFriendRequest(String sessionToken, String requestId, UUID friendshipId);
    FriendDashboard removeFriend(String sessionToken, String requestId, UUID friendshipId);
    java.util.List<AdminAccountView> adminAccounts(String sessionToken);
    java.util.List<AdminFriendshipView> adminFriendships(String sessionToken);
    AdminWalletAdjustment adjustAccountWallet(String sessionToken, String requestId, UUID accountId,
                                              long chipDelta, long crystalDelta, String reason);
    void resetAccountPassword(String sessionToken, String requestId, UUID accountId, String newPassword);
    AdminFriendshipView createAdminFriendship(String sessionToken, String requestId,
                                              UUID firstAccountId, UUID secondAccountId);
    void removeAdminFriendship(String sessionToken, String requestId, UUID friendshipId);
    Leaderboards leaderboards();
}
