package com.xidao.poker;

import com.xidao.poker.application.room.GameApplicationService;
import com.xidao.poker.application.room.RoomService;
import com.xidao.poker.application.history.HandHistoryPublisher;
import com.xidao.poker.application.history.NoOpHandHistoryPublisher;
import com.xidao.poker.application.arena.ArenaApplicationService;
import com.xidao.poker.web.arena.ArenaWebSocketHandler;
import com.xidao.poker.web.ws.PokerWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PokerApplicationContextTest {
    @Autowired
    private RoomService roomService;

    @Autowired
    private GameApplicationService gameApplicationService;

    @Autowired
    private PokerWebSocketHandler webSocketHandler;

    @Autowired
    private ArenaApplicationService arenaApplicationService;

    @Autowired
    private ArenaWebSocketHandler arenaWebSocketHandler;

    @Autowired
    private HandHistoryPublisher handHistoryPublisher;

    @Test
    void contextLoadsWithoutAConfiguredDatabase() {
        assertThat(roomService).isNotNull();
        assertThat(gameApplicationService).isNotNull();
        assertThat(webSocketHandler).isNotNull();
        assertThat(arenaApplicationService).isNotNull();
        assertThat(arenaWebSocketHandler).isNotNull();
        assertThat(handHistoryPublisher).isSameAs(NoOpHandHistoryPublisher.INSTANCE);
    }
}
