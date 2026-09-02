package com.xidao.poker.web.api;

import com.xidao.poker.application.account.AccountService;
import com.xidao.poker.application.arena.ArenaApplicationService;
import com.xidao.poker.application.arena.ArenaRoomSummary;
import com.xidao.poker.config.PokerPersistenceProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/arena/rooms")
public class ArenaRoomController {
    private final ArenaApplicationService arenas;
    private final AccountService accounts;
    private final boolean authenticationRequired;

    public ArenaRoomController(ArenaApplicationService arenas,
                               ObjectProvider<AccountService> accountProvider,
                               PokerPersistenceProperties persistence) {
        this.arenas = arenas;
        this.accounts = accountProvider.getIfAvailable();
        this.authenticationRequired = persistence.enabled();
    }

    @GetMapping
    public List<ArenaRoomSummary> list() {
        return arenas.list();
    }

    @PostMapping
    public ResponseEntity<ArenaRoomSummary> create(@Valid @RequestBody CreateArenaRoomRequest request,
                                                    HttpServletRequest servletRequest) {
        if (authenticationRequired) {
            if (accounts == null) throw new IllegalStateException("account service is unavailable");
            accounts.authenticate(SessionCookie.require(servletRequest));
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(arenas.create(request.roomName(), request.maxPlayers()));
    }
}
