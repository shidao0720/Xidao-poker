package com.xidao.poker.persistence.history;

import com.xidao.poker.application.history.CompletedHandArchive;
import com.xidao.poker.application.history.HandHistoryRepository;
import com.xidao.poker.application.history.HandHistorySaveResult;
import com.xidao.poker.engine.history.CompletedHandAction;
import com.xidao.poker.engine.history.CompletedHandSnapshot;
import com.xidao.poker.test.HistoryFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "poker.persistence.enabled=true"
)
@Testcontainers(disabledWithoutDocker = true)
class PostgresHandHistoryIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("poker.persistence.database.jdbc-url", POSTGRES::getJdbcUrl);
        registry.add("poker.persistence.database.username", POSTGRES::getUsername);
        registry.add("poker.persistence.database.password", POSTGRES::getPassword);
    }

    @Autowired
    private HandHistoryRepository repository;

    @Autowired
    private DataSource dataSource;

    @Test
    void migrationTransactionAndIdempotencyWorkAgainstPostgres() {
        CompletedHandArchive archive = HistoryFixtures.archive(1);

        assertThat(repository.save(archive)).isEqualTo(HandHistorySaveResult.SAVED);
        assertThat(repository.save(archive)).isEqualTo(HandHistorySaveResult.ALREADY_EXISTS);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hand_history", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hand_player", Long.class)).isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM game_action", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "SELECT hand_count FROM game_record WHERE game_id = ?", Long.class, archive.gameId()))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "SELECT SUM(hands_played) FROM player_statistic", Long.class)).isEqualTo(2L);
    }

    @Test
    void childInsertFailureRollsBackTheWholeHand() {
        CompletedHandArchive original = HistoryFixtures.archive(2);
        CompletedHandAction invalidAction = new CompletedHandAction(
                1,
                original.hand().actions().getFirst().turnId(),
                "missing-player",
                original.hand().actions().getFirst().phase(),
                original.hand().actions().getFirst().action(),
                0,
                0,
                0,
                10,
                false
        );
        CompletedHandSnapshot invalidHand = new CompletedHandSnapshot(
                original.hand().sessionId(),
                original.hand().handId(),
                original.hand().config(),
                original.hand().buttonSeat(),
                original.hand().smallBlindSeat(),
                original.hand().bigBlindSeat(),
                original.hand().totalPot(),
                original.hand().communityCards(),
                original.hand().pots(),
                original.hand().awards(),
                original.hand().players(),
                List.of(invalidAction),
                true
        );
        CompletedHandArchive invalid = new CompletedHandArchive(
                original.roomName(),
                original.roomCreatedAt(),
                original.startedAt(),
                original.endedAt(),
                invalidHand
        );

        assertThatThrownBy(() -> repository.save(invalid)).isInstanceOf(RuntimeException.class);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM hand_history WHERE game_id = ? AND hand_id = ?",
                Long.class,
                invalid.gameId(),
                invalid.handId()
        )).isZero();
    }
}
