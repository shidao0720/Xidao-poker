package com.xidao.poker.web.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateArenaRoomRequest(
        @NotBlank @Size(max = 40) String roomName,
        @Min(2) @Max(10) int maxPlayers
) {
    public CreateArenaRoomRequest {
        if (roomName != null) roomName = roomName.strip();
    }
}
