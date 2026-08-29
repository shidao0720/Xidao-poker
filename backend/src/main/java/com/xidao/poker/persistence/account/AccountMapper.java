package com.xidao.poker.persistence.account;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Mapper
public interface AccountMapper {
    @Select("""
            SELECT a.account_id, a.real_name_key, a.password_hash, a.primary_game_id, a.avatar_key,
                   w.chip_balance, w.crystal_balance
            FROM identity_account a
            JOIN account_wallet w ON w.account_id = a.account_id
            WHERE a.real_name_key = #{key}
            FOR UPDATE OF a, w
            """)
    AccountRow lockByRealNameKey(@Param("key") String key);

    @Select("""
            SELECT a.account_id, a.real_name_key, a.password_hash, a.primary_game_id, a.avatar_key,
                   w.chip_balance, w.crystal_balance
            FROM poker_user u
            JOIN identity_account a ON a.account_id = u.account_id
            JOIN account_wallet w ON w.account_id = a.account_id
            WHERE u.player_id = #{gameId}
            """)
    AccountRow findByGameId(@Param("gameId") String gameId);

    @Insert("""
            INSERT INTO identity_account
                (account_id, real_name, real_name_key, password_hash, created_at, updated_at)
            VALUES (#{accountId}, #{realName}, #{realNameKey}, #{passwordHash}, #{now}, #{now})
            """)
    int insertAccount(UUID accountId, String realName, String realNameKey, String passwordHash, Instant now);

    @Insert("""
            INSERT INTO account_wallet (account_id, chip_balance, crystal_balance, updated_at)
            VALUES (#{accountId}, 10000, 0, #{now})
            """)
    int insertWallet(UUID accountId, Instant now);

    @Insert("""
            INSERT INTO wallet_ledger
                (ledger_id, account_id, currency, delta, balance_after, reason, request_id, created_at)
            VALUES (#{ledgerId}, #{accountId}, #{currency}, #{delta}, #{balanceAfter},
                    #{reason}, #{requestId}, #{now})
            """)
    int insertLedger(UUID ledgerId, UUID accountId, String currency, long delta,
                     long balanceAfter, String reason, String requestId, Instant now);

    @Insert("""
            INSERT INTO poker_user (player_id, display_name, account_id, created_at, updated_at)
            VALUES (#{gameId}, #{gameId}, #{accountId}, #{now}, #{now})
            ON CONFLICT (player_id) DO NOTHING
            """)
    int insertGameId(String gameId, UUID accountId, Instant now);

    @Update("""
            UPDATE poker_user SET account_id = #{accountId}, updated_at = #{now}
            WHERE player_id = #{gameId} AND (account_id IS NULL OR account_id = #{accountId})
            """)
    int attachExistingGameId(String gameId, UUID accountId, Instant now);

    @Update("""
            UPDATE identity_account SET primary_game_id = #{gameId}, updated_at = #{now}
            WHERE account_id = #{accountId} AND primary_game_id IS NULL
            """)
    int setPrimaryGameId(UUID accountId, String gameId, Instant now);

    @Update("UPDATE identity_account SET avatar_key = #{avatarKey}, updated_at = #{now} WHERE account_id = #{accountId}")
    int updateAvatar(UUID accountId, String avatarKey, Instant now);

    @Select("SELECT player_id FROM poker_user WHERE account_id = #{accountId} ORDER BY player_id")
    List<String> listGameIds(@Param("accountId") UUID accountId);

    @Select("SELECT account_id FROM poker_user WHERE player_id = #{gameId} FOR UPDATE")
    UUID lockGameIdOwner(@Param("gameId") String gameId);

    @Insert("""
            INSERT INTO account_session (token_hash, account_id, game_id, created_at, expires_at)
            VALUES (#{tokenHash}, #{accountId}, #{gameId}, #{now}, #{expiresAt})
            """)
    int insertSession(String tokenHash, UUID accountId, String gameId, Instant now, Instant expiresAt);

    @Select("""
            SELECT account_id, game_id, expires_at FROM account_session
            WHERE token_hash = #{tokenHash} AND revoked_at IS NULL AND expires_at > #{now}
            """)
    SessionRow findSession(String tokenHash, Instant now);

    @Update("UPDATE account_session SET revoked_at = #{now} WHERE token_hash = #{tokenHash} AND revoked_at IS NULL")
    int revokeSession(String tokenHash, Instant now);

    @Select("SELECT chip_balance FROM account_wallet WHERE account_id = #{accountId} FOR UPDATE")
    Long lockWallet(@Param("accountId") UUID accountId);

    @Select("SELECT crystal_balance FROM account_wallet WHERE account_id = #{accountId}")
    long crystalBalance(@Param("accountId") UUID accountId);

    @Insert("""
            INSERT INTO daily_check_in (account_id, check_in_date, request_id, awarded_chips, created_at)
            VALUES (#{accountId}, #{date}, #{requestId}, #{chips}, #{now})
            ON CONFLICT (account_id, check_in_date) DO NOTHING
            """)
    int insertCheckIn(UUID accountId, LocalDate date, String requestId, long chips, Instant now);

    @Update("""
            UPDATE account_wallet
            SET chip_balance = chip_balance + #{chips}, version = version + 1, updated_at = #{now}
            WHERE account_id = #{accountId}
            """)
    int addChips(UUID accountId, long chips, Instant now);

    @Update("""
            UPDATE account_wallet
            SET chip_balance = chip_balance - #{chipCost},
                crystal_balance = crystal_balance + #{crystals},
                version = version + 1, updated_at = #{now}
            WHERE account_id = #{accountId} AND chip_balance >= #{chipCost}
            """)
    int convertToCrystals(UUID accountId, long chipCost, long crystals, Instant now);

    @Select("""
            SELECT EXISTS(
                SELECT 1 FROM wallet_ledger
                WHERE account_id = #{accountId} AND request_id = #{requestId}
            )
            """)
    boolean ledgerRequestExists(UUID accountId, String requestId);

    @Select("SELECT chip_balance FROM account_wallet WHERE account_id = #{accountId}")
    long chipBalance(@Param("accountId") UUID accountId);

    @Select("""
            SELECT escrow_id, account_id, game_id, room_id, buy_in, returned_chips, status
            FROM table_buy_in WHERE account_id = #{accountId} AND status = 'ACTIVE'
            FOR UPDATE
            """)
    TableEscrowRow lockActiveEscrow(@Param("accountId") UUID accountId);

    @Select("""
            SELECT escrow_id, account_id, game_id, room_id, buy_in, returned_chips, status
            FROM table_buy_in
            WHERE room_id = #{roomId} AND game_id = #{gameId} AND status = 'ACTIVE'
            """)
    TableEscrowRow findActiveEscrowBySeat(String roomId, String gameId);

    @Select("""
            SELECT escrow_id, account_id, game_id, room_id, buy_in, returned_chips, status
            FROM table_buy_in WHERE settle_request_id = #{requestId}
            """)
    TableEscrowRow findEscrowBySettlementRequest(@Param("requestId") String requestId);

    @Select("""
            SELECT escrow_id, account_id, game_id, room_id, buy_in, returned_chips, status
            FROM table_buy_in
            WHERE room_id = #{roomId} AND game_id = #{gameId} AND status = 'ACTIVE'
            FOR UPDATE
            """)
    TableEscrowRow lockActiveEscrowBySeat(String roomId, String gameId);

    @Update("""
            UPDATE account_wallet SET chip_balance = chip_balance - #{buyIn},
                version = version + 1, updated_at = #{now}
            WHERE account_id = #{accountId} AND chip_balance >= #{buyIn}
            """)
    int debitBuyIn(UUID accountId, long buyIn, Instant now);

    @Insert("""
            INSERT INTO table_buy_in
                (escrow_id, account_id, game_id, room_id, buy_in, status,
                 reserve_request_id, created_at)
            VALUES (#{escrowId}, #{accountId}, #{gameId}, #{roomId}, #{buyIn}, 'ACTIVE',
                    #{requestId}, #{now})
            """)
    int insertEscrow(UUID escrowId, UUID accountId, String gameId, String roomId,
                     long buyIn, String requestId, Instant now);

    @Update("""
            UPDATE table_buy_in SET status = 'SETTLED', returned_chips = #{finalStack},
                settle_request_id = #{requestId}, settled_at = #{now}
            WHERE escrow_id = #{escrowId} AND status = 'ACTIVE'
            """)
    int settleEscrow(UUID escrowId, long finalStack, String requestId, Instant now);

    @Select("""
            SELECT a.primary_game_id AS game_id, COALESCE(SUM(s.hands_won), 0) AS value
            FROM identity_account a
            JOIN poker_user u ON u.account_id = a.account_id
            JOIN player_statistic s ON s.player_id = u.player_id
            WHERE a.primary_game_id IS NOT NULL
            GROUP BY a.account_id, a.primary_game_id
            ORDER BY value DESC, a.primary_game_id ASC LIMIT 3
            """)
    List<RankingRow> topHandsWon();

    @Select("""
            SELECT a.primary_game_id AS game_id, COALESCE(SUM(s.total_winnings), 0) AS value
            FROM identity_account a
            JOIN poker_user u ON u.account_id = a.account_id
            JOIN player_statistic s ON s.player_id = u.player_id
            WHERE a.primary_game_id IS NOT NULL
            GROUP BY a.account_id, a.primary_game_id
            ORDER BY value DESC, a.primary_game_id ASC LIMIT 3
            """)
    List<RankingRow> topTotalWinnings();

    @Select("""
            SELECT a.primary_game_id AS game_id,
                   MAX(GREATEST(hp.winnings - hp.total_contribution, 0)) AS value
            FROM identity_account a
            JOIN poker_user u ON u.account_id = a.account_id
            JOIN hand_player hp ON hp.player_id = u.player_id
            WHERE a.primary_game_id IS NOT NULL
            GROUP BY a.account_id, a.primary_game_id
            ORDER BY value DESC, a.primary_game_id ASC LIMIT 3
            """)
    List<RankingRow> topSingleHandGain();
}
