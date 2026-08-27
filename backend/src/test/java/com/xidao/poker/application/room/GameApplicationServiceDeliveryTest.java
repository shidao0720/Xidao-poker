package com.xidao.poker.application.room;

import com.xidao.poker.engine.game.GameConfig;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class GameApplicationServiceDeliveryTest {
    @Test
    void committedCommandIsNotReportedAsFailedWhenDeliverySchedulingIsRejected() {
        RoomRegistry rooms = new RoomRegistry();
        new RoomService(rooms, Clock.systemUTC()).createRoom(
                "room", "LAN", new GameConfig(5, 10, 1_000, 10)
        );
        RoomEventDispatcher dispatcher = new RoomEventDispatcher(
                rooms,
                (roomId, delivery) -> { },
                command -> { throw new RejectedExecutionException("simulated shutdown"); }
        );
        GameApplicationService games = new GameApplicationService(rooms, dispatcher);

        assertThatCode(() -> games.join("room", "join-A", "A", "Alice", "socket-A"))
                .doesNotThrowAnyException();
        assertThat(rooms.require("room").pendingDeliveryCount()).isPositive();
    }
}
