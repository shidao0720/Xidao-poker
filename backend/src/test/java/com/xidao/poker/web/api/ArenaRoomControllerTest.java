package com.xidao.poker.web.api;

import com.xidao.poker.application.account.AccountService;
import com.xidao.poker.application.arena.ArenaApplicationService;
import com.xidao.poker.application.arena.ArenaRoomRegistry;
import com.xidao.poker.config.PokerPersistenceProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ArenaRoomControllerTest {
    private ScheduledExecutorService scheduler;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        ArenaRoomRegistry registry = new ArenaRoomRegistry(scheduler, (roomId, update) -> { }, Clock.systemUTC());
        ArenaApplicationService service = new ArenaApplicationService(registry);
        PokerPersistenceProperties persistence = new PokerPersistenceProperties(false, 1, 16, 1,
                Duration.ZERO, Duration.ZERO, null);
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        mvc = MockMvcBuilders.standaloneSetup(new ArenaRoomController(service,
                        beans.getBeanProvider(AccountService.class), persistence))
                .setControllerAdvice(new RestExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    @Test
    void createsAndListsAValidArenaRoom() throws Exception {
        mvc.perform(post("/api/arena/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomName":"  Vector Duel  ","maxPlayers":10}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomName").value("Vector Duel"))
                .andExpect(jsonPath("$.maxPlayers").value(10))
                .andExpect(jsonPath("$.players").value(0));

        mvc.perform(get("/api/arena/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roomName").value("Vector Duel"));
    }

    @Test
    void rejectsCapacityAboveTen() throws Exception {
        mvc.perform(post("/api/arena/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomName":"Invalid","maxPlayers":11}
                                """))
                .andExpect(status().isBadRequest());
    }
}
