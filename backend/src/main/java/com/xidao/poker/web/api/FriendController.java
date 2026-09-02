package com.xidao.poker.web.api;

import com.xidao.poker.application.account.AccountService;
import com.xidao.poker.application.account.FriendDashboard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/friends")
@ConditionalOnProperty(prefix = "poker.persistence", name = "enabled", havingValue = "true")
public class FriendController {
    private final AccountService accounts;

    public FriendController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    public FriendDashboard friends(HttpServletRequest request) {
        return accounts.friends(SessionCookie.require(request));
    }

    @PostMapping("/heartbeat")
    public void heartbeat(HttpServletRequest request, @Valid @RequestBody CheckInRequest command) {
        accounts.heartbeat(SessionCookie.require(request), command.requestId());
    }

    @PostMapping("/requests")
    public FriendDashboard send(HttpServletRequest request, @Valid @RequestBody FriendRequestRequest command) {
        return accounts.sendFriendRequest(SessionCookie.require(request), command.requestId(), command.gameId());
    }

    @PostMapping("/{friendshipId}/accept")
    public FriendDashboard accept(HttpServletRequest request, @PathVariable UUID friendshipId,
                                  @Valid @RequestBody CheckInRequest command) {
        return accounts.acceptFriendRequest(SessionCookie.require(request), command.requestId(), friendshipId);
    }

    @PostMapping("/{friendshipId}/reject")
    public FriendDashboard reject(HttpServletRequest request, @PathVariable UUID friendshipId,
                                  @Valid @RequestBody CheckInRequest command) {
        return accounts.rejectFriendRequest(SessionCookie.require(request), command.requestId(), friendshipId);
    }

    @PostMapping("/{friendshipId}/remove")
    public FriendDashboard remove(HttpServletRequest request, @PathVariable UUID friendshipId,
                                  @Valid @RequestBody CheckInRequest command) {
        return accounts.removeFriend(SessionCookie.require(request), command.requestId(), friendshipId);
    }
}
