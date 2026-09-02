package com.xidao.poker.web.spa;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SpaForwardControllerTest {
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new SpaForwardController()).build();

    @Test
    void roomRouteFallsBackToSpaIndex() throws Exception {
        mvc.perform(get("/rooms/room-123"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void portalRoutesFallBackToSpaIndex() throws Exception {
        for (String route : new String[]{"/play", "/leaderboard", "/store", "/profile", "/friends", "/admin", "/arena", "/arena/arena_test"}) {
            mvc.perform(get(route))
                    .andExpect(status().isOk())
                    .andExpect(forwardedUrl("/index.html"));
        }
    }

    @Test
    void apiAndStaticPathsAreNotCapturedBySpaFallback() throws Exception {
        mvc.perform(get("/api/rooms/missing")).andExpect(status().isNotFound());
        mvc.perform(get("/assets/missing.css")).andExpect(status().isNotFound());
        mvc.perform(get("/ws/missing")).andExpect(status().isNotFound());
    }
}
