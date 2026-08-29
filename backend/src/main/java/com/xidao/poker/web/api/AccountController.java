package com.xidao.poker.web.api;

import com.xidao.poker.application.account.*;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @GetMapping("/api/leaderboards")
    public Leaderboards leaderboards() { return accounts.leaderboards(); }

    @GetMapping("/api/shop/items")
    public List<Object> shopItems() { return List.of(); }
}
