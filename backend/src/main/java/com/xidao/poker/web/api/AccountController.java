package com.xidao.poker.web.api;

import com.xidao.poker.application.account.*;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(prefix = "poker.persistence", name = "enabled", havingValue = "true")
public class AccountController {
    private final AccountService accounts;

    public AccountController(AccountService accounts) { this.accounts = accounts; }

    @GetMapping("/api/account/me")
    public AccountProfile profile(HttpServletRequest request) {
        return accounts.profile(SessionCookie.require(request));
    }

    @PutMapping("/api/account/avatar")
    public AccountProfile updateAvatar(HttpServletRequest servletRequest,
                                      @Valid @RequestBody AvatarUpdateRequest request) {
        return accounts.updateAvatar(SessionCookie.require(servletRequest), request.avatarKey());
    }

    @PostMapping("/api/account/check-in")
    public CheckInResult checkIn(HttpServletRequest servletRequest,
                                 @Valid @RequestBody CheckInRequest request) {
        return accounts.checkIn(SessionCookie.require(servletRequest), request.requestId());
    }

    @PostMapping("/api/wallet/exchange")
    public WalletSnapshot exchange(HttpServletRequest servletRequest,
                                   @Valid @RequestBody WalletCommandRequest request) {
        return accounts.exchangeForCrystals(
                SessionCookie.require(servletRequest), request.requestId(), request.chips());
    }

    @PostMapping("/api/account/redeem")
    public RedemptionResult redeem(HttpServletRequest servletRequest,
                                   @Valid @RequestBody RedemptionRequest request) {
        return accounts.redeemCode(
                SessionCookie.require(servletRequest), request.requestId(), request.code());
    }

    @GetMapping("/api/account/table-result")
    public TableSessionResult tableResult(HttpServletRequest servletRequest,
                                          @RequestParam String roomId) {
        return accounts.tableSessionResult(SessionCookie.require(servletRequest), roomId);
    }

    @GetMapping("/api/account/mail")
    public MailInbox inbox(HttpServletRequest request) {
        return accounts.inbox(SessionCookie.require(request));
    }

    @PutMapping("/api/account/mail/{mailId}/read")
    public MailItem markRead(HttpServletRequest request, @PathVariable java.util.UUID mailId) {
        return accounts.markMailRead(SessionCookie.require(request), mailId);
    }

    @PostMapping("/api/account/mail/{mailId}/claim")
    public MailClaimResult claim(HttpServletRequest servletRequest, @PathVariable java.util.UUID mailId,
                                 @Valid @RequestBody CheckInRequest request) {
        return accounts.claimMail(SessionCookie.require(servletRequest), mailId, request.requestId());
    }

    @PostMapping("/api/admin/mail/broadcast")
    public MailItem broadcast(HttpServletRequest servletRequest,
                              @Valid @RequestBody BroadcastMailRequest request) {
        return accounts.broadcastMail(SessionCookie.require(servletRequest), request.requestId(),
                new BroadcastMailCommand(request.type(), request.subject(), request.body(),
                        request.rewardChips(), request.rewardCrystals(), request.rewardSkinKey()));
    }

    @GetMapping("/api/admin/overview")
    public AdminOverview adminOverview(HttpServletRequest request) {
        return accounts.adminOverview(SessionCookie.require(request));
    }

    @GetMapping("/api/admin/redemption-codes")
    public java.util.List<AdminRedemptionCode> redemptionCodes(HttpServletRequest request) {
        return accounts.adminRedemptionCodes(SessionCookie.require(request));
    }

    @PostMapping("/api/admin/redemption-codes")
    public AdminRedemptionCodeCreated createRedemptionCode(
            HttpServletRequest servletRequest,
            @Valid @RequestBody CreateRedemptionCodeRequest request
    ) {
        return accounts.createRedemptionCode(SessionCookie.require(servletRequest), request.requestId(),
                new CreateRedemptionCodeCommand(request.code(), request.currency(), request.rewardAmount(),
                        request.maxRedemptions(), request.validFrom(), request.validUntil()));
    }

    @PutMapping("/api/admin/redemption-codes/{codeHash}/enabled")
    public AdminRedemptionCode setRedemptionCodeEnabled(
            HttpServletRequest servletRequest,
            @PathVariable @jakarta.validation.constraints.Pattern(regexp = "[a-fA-F0-9]{64}") String codeHash,
            @Valid @RequestBody RedemptionCodeEnabledRequest request
    ) {
        return accounts.setRedemptionCodeEnabled(SessionCookie.require(servletRequest),
                request.requestId(), codeHash, request.enabled());
    }

    @GetMapping("/api/admin/accounts")
    public java.util.List<AdminAccountView> adminAccounts(HttpServletRequest request) {
        return accounts.adminAccounts(SessionCookie.require(request));
    }

    @GetMapping("/api/admin/friendships")
    public java.util.List<AdminFriendshipView> adminFriendships(HttpServletRequest request) {
        return accounts.adminFriendships(SessionCookie.require(request));
    }

    @PostMapping("/api/admin/accounts/{accountId}/wallet-adjustments")
    public AdminWalletAdjustment adjustWallet(
            HttpServletRequest request, @PathVariable java.util.UUID accountId,
            @Valid @RequestBody AdminWalletAdjustmentRequest command
    ) {
        return accounts.adjustAccountWallet(SessionCookie.require(request), command.requestId(), accountId,
                command.chipDelta(), command.crystalDelta(), command.reason());
    }

    @PostMapping("/api/admin/accounts/{accountId}/password-reset")
    public void resetPassword(
            HttpServletRequest request, @PathVariable java.util.UUID accountId,
            @Valid @RequestBody AdminPasswordResetRequest command
    ) {
        accounts.resetAccountPassword(SessionCookie.require(request), command.requestId(),
                accountId, command.newPassword());
    }

    @PostMapping("/api/admin/friendships")
    public AdminFriendshipView createFriendship(
            HttpServletRequest request, @Valid @RequestBody AdminFriendshipRequest command
    ) {
        return accounts.createAdminFriendship(SessionCookie.require(request), command.requestId(),
                command.firstAccountId(), command.secondAccountId());
    }

    @PostMapping("/api/admin/friendships/{friendshipId}/remove")
    public void removeFriendship(
            HttpServletRequest request, @PathVariable java.util.UUID friendshipId,
            @Valid @RequestBody CheckInRequest command
    ) {
        accounts.removeAdminFriendship(SessionCookie.require(request), command.requestId(), friendshipId);
    }

    @GetMapping("/api/store/catalog")
    public java.util.List<StoreItem> storeCatalog(HttpServletRequest request) {
        return accounts.storeCatalog(SessionCookie.require(request));
    }

    @PostMapping("/api/store/purchase")
    public StorePurchaseResult purchase(HttpServletRequest servletRequest,
                                        @Valid @RequestBody StorePurchaseRequest request) {
        return accounts.purchaseCosmetic(SessionCookie.require(servletRequest),
                request.requestId(), request.itemKey());
    }

    @PutMapping("/api/account/loadout/{slot}")
    public AccountProfile equip(HttpServletRequest servletRequest, @PathVariable String slot,
                                @Valid @RequestBody CosmeticEquipRequest request) {
        return accounts.equipCosmetic(SessionCookie.require(servletRequest), request.requestId(),
                CosmeticSlot.fromApi(slot), request.itemKey());
    }

    @GetMapping("/api/leaderboards")
    public Leaderboards leaderboards() { return accounts.leaderboards(); }

}
