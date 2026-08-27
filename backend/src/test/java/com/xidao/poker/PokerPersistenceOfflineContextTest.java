package com.xidao.poker;

import com.xidao.poker.application.history.AsyncHandHistoryWriter;
import com.xidao.poker.application.history.HandHistoryPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "poker.persistence.enabled=true",
                "poker.persistence.database.jdbc-url=jdbc:postgresql://127.0.0.1:1/unavailable",
                "poker.persistence.database.username=test",
                "poker.persistence.database.password=test"
        }
)
class PokerPersistenceOfflineContextTest {
    @Autowired
    private HandHistoryPublisher publisher;

    @Test
    void enabledPersistenceDoesNotRequireDatabaseDuringRealtimeServiceStartup() {
        assertThat(publisher).isInstanceOf(AsyncHandHistoryWriter.class);
    }
}
