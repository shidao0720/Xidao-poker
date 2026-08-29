package com.xidao.poker.web.api;

import com.xidao.poker.config.PokerPersistenceProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RuntimeController {
    private final PokerPersistenceProperties persistence;

    public RuntimeController(PokerPersistenceProperties persistence) {
        this.persistence = persistence;
    }

    @GetMapping("/api/runtime")
    public RuntimeCapabilities capabilities() {
        return new RuntimeCapabilities(persistence.enabled(), 10, true);
    }

    public record RuntimeCapabilities(
            boolean accountsEnabled,
            int chipsPerSpiritCrystal,
            boolean playMoneyOnly
    ) { }
}
