package com.xidao.poker.web.api;

import com.xidao.poker.application.room.RoomService;
import com.xidao.poker.application.room.RoomSummary;
import com.xidao.poker.engine.game.GameConfig;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** 大厅 HTTP API。加入牌局本身由 WebSocket 握手完成。 */
@RestController
@RequestMapping("/api/rooms")
public class RoomController {
    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping
    public List<RoomSummary> listRooms() {
        return roomService.listRooms();
    }

    @PostMapping
    public ResponseEntity<RoomSummary> createRoom(@Valid @RequestBody CreateRoomRequest request) {
        GameConfig config = new GameConfig(
                request.smallBlind(),
                request.bigBlind(),
                request.buyIn(),
                request.maxPlayers()
        );
        RoomSummary room = roomService.createRoom(
                UUID.randomUUID().toString(),
                request.roomName(),
                config
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(room);
    }

    @DeleteMapping("/{roomId}")
    public ResponseEntity<Void> removeEmptyRoom(@PathVariable String roomId) {
        roomService.removeEmptyRoom(roomId);
        return ResponseEntity.noContent().build();
    }
}
