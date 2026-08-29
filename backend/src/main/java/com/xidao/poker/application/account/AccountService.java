package com.xidao.poker.application.account;

public interface AccountService {
    AuthResult register(String realName, String gameId, String password);
    AuthResult login(String realName, String gameId, String password);
    void logout(String sessionToken);
    AccountPrincipal authenticate(String sessionToken);
    AccountProfile profile(String sessionToken);
    AccountProfile updateAvatar(String sessionToken, String avatarKey);
    CheckInResult checkIn(String sessionToken, String requestId);
    WalletSnapshot exchangeForCrystals(String sessionToken, String requestId, long chips);
    Leaderboards leaderboards();
}
