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
import com.xidao.poker.application.account.RedemptionResult;
import com.xidao.poker.application.account.TableSessionResult;
import com.xidao.poker.application.account.BroadcastMailCommand;
import com.xidao.poker.application.account.CosmeticSlot;
import com.xidao.poker.application.account.CreateRedemptionCodeCommand;
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
import java.time.Instant;

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
    void administratorMailRewardsAndRedemptionCodeAreTransactionalAndIdempotent() {
        AuthResult account = accounts.register("测试邮件", "mail_master", "correct-horse-5");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("UPDATE identity_account SET is_admin = TRUE WHERE account_id = "
                + "(SELECT account_id FROM poker_user WHERE player_id = 'mail_master')");

        var sent = accounts.broadcastMail(account.sessionToken(), "broadcast-request-001",
                new BroadcastMailCommand("REWARD", "维护奖励", "感谢参与本次测试。",
                        500, 25, "observer-frame"));
        var inbox = accounts.inbox(account.sessionToken());
        assertThat(inbox.unreadCount()).isPositive();
        assertThat(inbox.messages()).extracting("mailId").contains(sent.mailId());
        var claimed = accounts.claimMail(
                account.sessionToken(), sent.mailId(), "mailclaim-request-001");
        var duplicateClaim = accounts.claimMail(
                account.sessionToken(), sent.mailId(), "mailclaim-request-001");
        assertThat(claimed.wallet()).isEqualTo(new WalletSnapshot(10_500, 25));
        assertThat(duplicateClaim.wallet()).isEqualTo(claimed.wallet());
        assertThat(claimed.cosmetics()).contains("observer-frame");

        RedemptionResult redeemed = accounts.redeemCode(
                account.sessionToken(), "redeem-request-001", "fate-stay-poker");
        RedemptionResult repeatedRequest = accounts.redeemCode(
                account.sessionToken(), "redeem-request-001", "FATE-STAY-POKER");
        assertThat(redeemed.currency()).isEqualTo("CRYSTAL");
        assertThat(redeemed.amount()).isEqualTo(100);
        assertThat(redeemed.wallet()).isEqualTo(new WalletSnapshot(10_500, 125));
        assertThat(repeatedRequest).isEqualTo(redeemed);
        assertThatThrownBy(() -> accounts.redeemCode(
                account.sessionToken(), "redeem-request-002", "FATE-STAY-POKER"))
                .isInstanceOfSatisfying(AccountException.class,
                        error -> assertThat(error.code())
                                .isEqualTo(AccountErrorCode.REDEMPTION_ALREADY_USED));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM redemption_claim WHERE account_id = "
                + "(SELECT account_id FROM poker_user WHERE player_id = 'mail_master')", Long.class))
                .isEqualTo(1L);
    }

    @Test
    void administratorCanManageRedemptionCodesWithIdempotentRequests() {
        AuthResult account = accounts.register("Admin Test", "admin_codes", "correct-horse-7");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("UPDATE identity_account SET is_admin = TRUE WHERE account_id = "
                + "(SELECT account_id FROM poker_user WHERE player_id = 'admin_codes')");

        var created = accounts.createRedemptionCode(account.sessionToken(), "admin-code-request-001",
                new CreateRedemptionCodeCommand("ADMIN-TEST-001", "CHIP", 250, 10,
                        Instant.parse("2026-01-01T00:00:00Z"), null));
        var repeated = accounts.createRedemptionCode(account.sessionToken(), "admin-code-request-001",
                new CreateRedemptionCodeCommand("ADMIN-TEST-001", "CHIP", 250, 10,
                        Instant.parse("2026-01-01T00:00:00Z"), null));

        assertThat(repeated).isEqualTo(created);
        assertThat(accounts.adminOverview(account.sessionToken()).accounts()).isPositive();
        assertThat(accounts.adminRedemptionCodes(account.sessionToken()))
                .extracting("codeHash").contains(created.redemptionCode().codeHash());
        assertThat(accounts.setRedemptionCodeEnabled(account.sessionToken(), "admin-code-toggle-001",
                created.redemptionCode().codeHash(), false).enabled()).isFalse();
        assertThat(accounts.setRedemptionCodeEnabled(account.sessionToken(), "admin-code-toggle-001",
                created.redemptionCode().codeHash(), false).enabled()).isFalse();
    }

    @Test
    void friendRequestsAreMutualPersistentAndExposeServerPresence() {
        AuthResult alice = accounts.register("Friend Alice", "friend_alice", "correct-horse-8");
        AuthResult bob = accounts.register("Friend Bob", "friend_bob", "correct-horse-9");
        accounts.heartbeat(alice.sessionToken(), "presence-alice-001");
        accounts.heartbeat(bob.sessionToken(), "presence-bob-001");

        var sent = accounts.sendFriendRequest(alice.sessionToken(), "friend-send-001", "friend_bob");
        assertThat(sent.outgoingRequests()).extracting("gameId").containsExactly("friend_bob");
        var incoming = accounts.friends(bob.sessionToken()).incomingRequests();
        assertThat(incoming).extracting("gameId").containsExactly("friend_alice");

        var accepted = accounts.acceptFriendRequest(bob.sessionToken(), "friend-accept-001",
                incoming.getFirst().friendshipId());
        assertThat(accepted.friends()).extracting("gameId").containsExactly("friend_alice");
        assertThat(accounts.friends(alice.sessionToken()).friends().getFirst().online()).isTrue();

        var removed = accounts.removeFriend(alice.sessionToken(), "friend-remove-001",
                accepted.friends().getFirst().friendshipId());
        assertThat(removed.friends()).isEmpty();
        assertThat(accounts.removeFriend(alice.sessionToken(), "friend-remove-001",
                accepted.friends().getFirst().friendshipId()).friends()).isEmpty();
    }

    @Test
    void administratorCanAuditWalletResetPasswordAndManageFriendships() {
        AuthResult admin = accounts.register("Operations Admin", "ops_admin", "correct-horse-10");
        AuthResult target = accounts.register("Operations Target", "ops_target", "correct-horse-11");
        AuthResult peer = accounts.register("Operations Peer", "ops_peer", "correct-horse-12");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("UPDATE identity_account SET is_admin = TRUE WHERE account_id = "
                + "(SELECT account_id FROM poker_user WHERE player_id = 'ops_admin')");
        var targetAccount = accounts.adminAccounts(admin.sessionToken()).stream()
                .filter(row -> row.primaryGameId().equals("ops_target")).findFirst().orElseThrow();
        var peerAccount = accounts.adminAccounts(admin.sessionToken()).stream()
                .filter(row -> row.primaryGameId().equals("ops_peer")).findFirst().orElseThrow();

        var adjusted = accounts.adjustAccountWallet(admin.sessionToken(), "admin-wallet-001",
                targetAccount.accountId(), 750, 20, "integration test grant");
        var repeated = accounts.adjustAccountWallet(admin.sessionToken(), "admin-wallet-001",
                targetAccount.accountId(), 750, 20, "integration test grant");
        assertThat(adjusted.wallet()).isEqualTo(new WalletSnapshot(10_750, 20));
        assertThat(repeated).isEqualTo(adjusted);

        var friendship = accounts.createAdminFriendship(admin.sessionToken(), "admin-friend-001",
                targetAccount.accountId(), peerAccount.accountId());
        assertThat(friendship.status()).isEqualTo("ACCEPTED");
        assertThat(accounts.friends(target.sessionToken()).friends())
                .extracting("gameId").contains("ops_peer");
        accounts.removeAdminFriendship(admin.sessionToken(), "admin-friend-remove-001",
                friendship.friendshipId());
        assertThat(accounts.friends(target.sessionToken()).friends()).isEmpty();

        accounts.resetAccountPassword(admin.sessionToken(), "admin-password-001",
                targetAccount.accountId(), "replacement-horse-11");
        assertThatThrownBy(() -> accounts.profile(target.sessionToken()))
                .isInstanceOfSatisfying(AccountException.class,
                        error -> assertThat(error.code()).isEqualTo(AccountErrorCode.UNAUTHORIZED));
        assertThatThrownBy(() -> accounts.login("Operations Target", "ops_target", "correct-horse-11"))
                .isInstanceOfSatisfying(AccountException.class,
                        error -> assertThat(error.code()).isEqualTo(AccountErrorCode.INVALID_CREDENTIALS));
        assertThat(accounts.login("Operations Target", "ops_target", "replacement-horse-11").profile().gameId())
                .isEqualTo("ops_target");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_log WHERE admin_account_id = "
                + "(SELECT account_id FROM poker_user WHERE player_id = 'ops_admin')", Long.class))
                .isEqualTo(4L);
    }

    @Test
    void cosmeticPurchaseAndLoadoutAreTransactionalAuthoritativeAndIdempotent() {
        AuthResult account = accounts.register("测试商城", "store_master", "correct-horse-6");
        accounts.exchangeForCrystals(account.sessionToken(), "store-exchange-001", 5_000);

        var purchased = accounts.purchaseCosmetic(
                account.sessionToken(), "store-purchase-001", "observer-frame");
        var repeated = accounts.purchaseCosmetic(
                account.sessionToken(), "store-purchase-001", "observer-frame");
        assertThat(purchased.wallet()).isEqualTo(new WalletSnapshot(5_000, 180));
        assertThat(repeated.wallet()).isEqualTo(purchased.wallet());
        assertThat(purchased.cosmetics()).contains("observer-frame");

        var equipped = accounts.equipCosmetic(account.sessionToken(), "store-equip-001",
                CosmeticSlot.AVATAR_FRAME, "observer-frame");
        assertThat(equipped.loadout().avatarFrame()).isEqualTo("observer-frame");
        assertThatThrownBy(() -> accounts.equipCosmetic(account.sessionToken(), "store-equip-002",
                CosmeticSlot.CARD_BACK, "red-lance"))
                .isInstanceOfSatisfying(AccountException.class,
                        error -> assertThat(error.code()).isEqualTo(AccountErrorCode.INVALID_COSMETIC));

        var unequipped = accounts.equipCosmetic(account.sessionToken(), "store-equip-003",
                CosmeticSlot.AVATAR_FRAME, null);
        assertThat(unequipped.loadout().avatarFrame()).isNull();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cosmetic_purchase WHERE account_id = "
                + "(SELECT account_id FROM poker_user WHERE player_id = 'store_master')", Long.class))
                .isEqualTo(1L);
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
        assertThat(accounts.tableSessionResult(account.sessionToken(), "room-escrow-1"))
                .isEqualTo(TableSessionResult.settled(1_000, 1_275));
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
