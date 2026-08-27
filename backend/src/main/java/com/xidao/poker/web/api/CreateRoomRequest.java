package com.xidao.poker.web.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRoomRequest(
        @NotBlank @Size(max = 40) String roomName,
        @Min(1) @Max(Integer.MAX_VALUE) int smallBlind,
        @Min(2) @Max(Integer.MAX_VALUE) int bigBlind,
        @Min(2) @Max(Integer.MAX_VALUE) int buyIn,
        @Min(2) @Max(10) int maxPlayers
) {
    public CreateRoomRequest {
        if (roomName != null) roomName = roomName.trim();
    }
}
