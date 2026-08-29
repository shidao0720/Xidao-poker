package com.xidao.poker.web.api;

import com.xidao.poker.application.account.AccountService;
import com.xidao.poker.application.account.AuthResult;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;

@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty(prefix = "poker.persistence", name = "enabled", havingValue = "true")
public class AuthController {
    private final AccountService accounts;
    private final Clock clock;

    public AuthController(AccountService accounts, Clock pokerClock) {
        this.accounts = accounts;
        this.clock = pokerClock;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody IdentityRequest request) {
        return authenticated(accounts.register(request.realName(), request.gameId(), request.password()));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody IdentityRequest request) {
        return authenticated(accounts.login(request.realName(), request.gameId(), request.password()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String token = SessionCookie.optional(request);
        if (token != null) accounts.logout(token);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, SessionCookie.expired().toString())
                .build();
    }

    private ResponseEntity<AuthResponse> authenticated(AuthResult result) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        SessionCookie.active(result.sessionToken(), result.expiresAt(), clock.instant()).toString())
                .body(new AuthResponse(result.expiresAt(), result.profile()));
    }

}
