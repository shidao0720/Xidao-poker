package com.xidao.poker.web.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.xidao.poker.application.room.RoomRegistry;
import com.xidao.poker.application.room.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoomControllerTest {
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        RoomRegistry registry = new RoomRegistry();
        RoomService service = new RoomService(
                registry,
                Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC)
        );
        mockMvc = MockMvcBuilders
                .standaloneSetup(new RoomController(service))
                .setControllerAdvice(new RestExceptionHandler())
                .build();
        objectMapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
    }

    @Test
    void createsListsAndRemovesAnEmptyRoom() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roomName": "  Friday LAN  ",
                                  "smallBlind": 5,
                                  "bigBlind": 10,
                                  "buyIn": 1000,
                                  "maxPlayers": 10
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomName").value("Friday LAN"))
                .andExpect(jsonPath("$.smallBlind").value(5))
                .andExpect(jsonPath("$.bigBlind").value(10))
                .andExpect(jsonPath("$.buyIn").value(1000))
                .andExpect(jsonPath("$.maxPlayers").value(10))
                .andExpect(jsonPath("$.playerCount").value(0))
                .andReturn();

        JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString());
        String roomId = body.path("roomId").asText();
        assertThat(roomId).isNotBlank();

        mockMvc.perform(get("/api/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roomId").value(roomId));

        mockMvc.perform(delete("/api/rooms/{roomId}", roomId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void rejectsInvalidCrossFieldGameConfiguration() throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roomName": "bad blinds",
                                  "smallBlind": 10,
                                  "bigBlind": 10,
                                  "buyIn": 100,
                                  "maxPlayers": 2
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void returnsStableNotFoundError() throws Exception {
        mockMvc.perform(delete("/api/rooms/{roomId}", "missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
    }
}
