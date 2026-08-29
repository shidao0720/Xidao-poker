package com.xidao.poker.persistence.history;

import com.xidao.poker.application.history.CompletedHandArchive;
import com.xidao.poker.application.account.AccountErrorCode;
import com.xidao.poker.application.account.AccountException;
import com.xidao.poker.application.account.AccountService;
import com.xidao.poker.application.account.AuthResult;
import com.xidao.poker.application.account.CheckInResult;
import com.xidao.poker.application.account.WalletSnapshot;
import com.xidao.poker.application.account.TableEconomyService;
import com.xidao.poker.application.account.TableBuyInReservation;
import com.xidao.poker.web.lifecycle.TableSettlementListener;
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

    @Autowired
    private AccountService accounts;

    @Autowired
    private TableEconomyService tableEconomy;

    @Autowired
    private TableSettlementListener tableSettlementListener;

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

    @Test
    void identityAliasesCheckInAndOneWayExchangeAreTransactionalAndIdempotent() {
        AuthResult first = accounts.register("测试甲", "fate_mstr_a", "correct-horse-1");
        AuthResult alias = accounts.register("测试甲", "fate_mstr_b", "correct-horse-1");

        assertThat(first.sessionToken()).hasSizeGreaterThanOrEqualTo(40);
        assertThat(alias.profile().gameIds()).containsExactly("fate_mstr_a", "fate_mstr_b");
        assertThat(alias.profile().wallet()).isEqualTo(new WalletSnapshot(10_000, 0));

        CheckInResult checkIn = accounts.checkIn(first.sessionToken(), "checkin-request-001");
        CheckInResult duplicateDay = accounts.checkIn(first.sessionToken(), "checkin-request-002");
        assertThat(checkIn.awarded()).isTrue();
        assertThat(checkIn.wallet()).isEqualTo(new WalletSnapshot(10_500, 0));
        assertThat(duplicateDay.awarded()).isFalse();
        assertThat(duplicateDay.wallet()).isEqualTo(new WalletSnapshot(10_500, 0));

        WalletSnapshot exchanged = accounts.exchangeForCrystals(
                first.sessionToken(), "exchange-request-001", 100);
        WalletSnapshot duplicateExchange = accounts.exchangeForCrystals(
                first.sessionToken(), "exchange-request-001", 100);
        assertThat(exchanged).isEqualTo(new WalletSnapshot(10_400, 10));
        assertThat(duplicateExchange).isEqualTo(exchanged);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String passwordHash = jdbc.queryForObject(
                "SELECT password_hash FROM identity_account WHERE real_name_key = ?",
                String.class, "测试甲");
        assertThat(passwordHash).startsWith("$2").doesNotContain("correct-horse-1");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wallet_ledger WHERE account_id = "
                + "(SELECT account_id FROM poker_user WHERE player_id = 'fate_mstr_a')", Long.class))
                .isEqualTo(4L);
    }

    @Test
    void gameIdCannotMoveBetweenRealIdentitiesAndFailedExchangeDoesNotMutateWallet() {
        AuthResult account = accounts.register("测试乙", "unique_saber", "correct-horse-2");

        assertThatThrownBy(() -> accounts.register("测试丙", "unique_saber", "correct-horse-3"))
                .isInstanceOfSatisfying(AccountException.class,
                        error -> assertThat(error.code()).isEqualTo(AccountErrorCode.GAME_ID_TAKEN));
        assertThatThrownBy(() -> accounts.exchangeForCrystals(
                account.sessionToken(), "exchange-too-large", 20_000))
                .isInstanceOfSatisfying(AccountException.class,
                        error -> assertThat(error.code()).isEqualTo(AccountErrorCode.INSUFFICIENT_CHIPS));
        assertThat(accounts.profile(account.sessionToken()).wallet())
                .isEqualTo(new WalletSnapshot(10_000, 0));
    }

    @Test
    void tableBuyInIsReservedOnceAndFinalStackIsReturnedOnce() {
        assertThat(tableSettlementListener).isNotNull();
        AuthResult account = accounts.register("测试丁", "escrow_rin", "correct-horse-4");
        var principal = accounts.authenticate(account.sessionToken());

        TableBuyInReservation first = tableEconomy.reserveBuyIn(
                principal.accountId(), principal.gameId(), "room-escrow-1", 1_000, "buyin-request-001");
        TableBuyInReservation reconnect = tableEconomy.reserveBuyIn(
                principal.accountId(), principal.gameId(), "room-escrow-1", 1_000, "buyin-request-002");

        assertThat(first.newlyReserved()).isTrue();
        assertThat(first.wallet()).isEqualTo(new WalletSnapshot(9_000, 0));
        assertThat(reconnect.newlyReserved()).isFalse();
        assertThat(reconnect.wallet()).isEqualTo(first.wallet());
        assertThatThrownBy(() -> tableEconomy.reserveBuyIn(
                principal.accountId(), principal.gameId(), "room-escrow-2", 1_000, "buyin-request-003"))
                .isInstanceOfSatisfying(AccountException.class,
                        error -> assertThat(error.code()).isEqualTo(AccountErrorCode.ALREADY_AT_TABLE));

        WalletSnapshot settled = tableEconomy.settleSeat(
                "room-escrow-1", principal.gameId(), 1_275, "cashout-request-001");
        WalletSnapshot duplicate = tableEconomy.settleSeat(
                "room-escrow-1", principal.gameId(), 1_275, "cashout-request-001");

        assertThat(settled).isEqualTo(new WalletSnapshot(10_275, 0));
        assertThat(duplicate).isEqualTo(settled);
        TableBuyInReservation rejoined = tableEconomy.reserveBuyIn(
                principal.accountId(), principal.gameId(), "room-escrow-1", 500, "buyin-request-004");
        assertThat(rejoined.newlyReserved()).isTrue();
        assertThat(rejoined.wallet()).isEqualTo(new WalletSnapshot(9_775, 0));
        assertThat(tableEconomy.settleSeat(
                "room-escrow-1", principal.gameId(), 450, "cashout-request-002"))
                .isEqualTo(new WalletSnapshot(10_225, 0));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM table_buy_in WHERE room_id = 'room-escrow-1' AND status = 'SETTLED'",
                Long.class)).isEqualTo(2L);
    }
}
