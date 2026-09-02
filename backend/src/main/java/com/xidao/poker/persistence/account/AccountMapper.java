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

import com.xidao.poker.application.account.AdminOverview;

@Mapper
public interface AccountMapper {
    @Select("""
            SELECT a.account_id, a.real_name_key, a.password_hash, a.primary_game_id, a.avatar_key,
                   a.is_admin AS admin,
                   w.chip_balance, w.crystal_balance
            FROM identity_account a
            JOIN account_wallet w ON w.account_id = a.account_id
            WHERE a.real_name_key = #{key}
            FOR UPDATE OF a, w
            """)
    AccountRow lockByRealNameKey(@Param("key") String key);

    @Select("""
            SELECT a.account_id, a.real_name_key, a.password_hash, a.primary_game_id, a.avatar_key,
                   a.is_admin AS admin,
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

    @Update("""
            UPDATE identity_account SET is_admin = TRUE, updated_at = #{now}
            WHERE account_id = #{accountId}
              AND NOT EXISTS (SELECT 1 FROM identity_account WHERE is_admin = TRUE)
            """)
    int promoteIfNoAdmin(UUID accountId, Instant now);

    @Select("SELECT player_id FROM poker_user WHERE account_id = #{accountId} ORDER BY player_id")
    List<String> listGameIds(@Param("accountId") UUID accountId);

    @Select("SELECT skin_key FROM account_cosmetic WHERE account_id = #{accountId} ORDER BY acquired_at, skin_key")
    List<String> listCosmetics(@Param("accountId") UUID accountId);

    @Select("SELECT slot, skin_key FROM account_cosmetic_loadout WHERE account_id = #{accountId} ORDER BY slot")
    List<CosmeticLoadoutRow> listCosmeticLoadout(@Param("accountId") UUID accountId);

    @Select("SELECT EXISTS(SELECT 1 FROM account_cosmetic WHERE account_id = #{accountId} AND skin_key = #{skinKey})")
    boolean ownsCosmetic(UUID accountId, String skinKey);

    @Select("""
            SELECT purchase_id, catalog_key, crystal_cost
            FROM cosmetic_purchase WHERE account_id = #{accountId} AND request_id = #{requestId}
            """)
    CosmeticPurchaseRow findCosmeticPurchaseByRequest(UUID accountId, String requestId);

    @Insert("""
            INSERT INTO cosmetic_purchase
                (purchase_id, account_id, catalog_key, crystal_cost, request_id, purchased_at)
            VALUES (#{purchaseId}, #{accountId}, #{catalogKey}, #{crystalCost}, #{requestId}, #{now})
            """)
    int insertCosmeticPurchase(UUID purchaseId, UUID accountId, String catalogKey,
                               long crystalCost, String requestId, Instant now);

    @Update("""
            UPDATE account_wallet
            SET crystal_balance = crystal_balance - #{crystals}, version = version + 1, updated_at = #{now}
            WHERE account_id = #{accountId} AND crystal_balance >= #{crystals}
            """)
    int debitCrystals(UUID accountId, long crystals, Instant now);

    @Insert("""
            INSERT INTO account_cosmetic (account_id, skin_key, acquired_at, source_purchase_id)
            VALUES (#{accountId}, #{skinKey}, #{now}, #{purchaseId})
            ON CONFLICT (account_id, skin_key) DO NOTHING
            """)
    int grantPurchasedCosmetic(UUID accountId, String skinKey, Instant now, UUID purchaseId);

    @Insert("""
            INSERT INTO account_cosmetic_loadout (account_id, slot, skin_key, equipped_at)
            VALUES (#{accountId}, #{slot}, #{skinKey}, #{now})
            ON CONFLICT (account_id, slot) DO UPDATE
            SET skin_key = EXCLUDED.skin_key, equipped_at = EXCLUDED.equipped_at
            """)
    int equipCosmetic(UUID accountId, String slot, String skinKey, Instant now);

    @org.apache.ibatis.annotations.Delete("DELETE FROM account_cosmetic_loadout WHERE account_id = #{accountId} AND slot = #{slot}")
    int unequipCosmetic(UUID accountId, String slot);

    @Select("SELECT account_id FROM poker_user WHERE player_id = #{gameId} FOR UPDATE")
    UUID lockGameIdOwner(@Param("gameId") String gameId);

    @Insert("""
            INSERT INTO account_session
                (token_hash, account_id, game_id, created_at, expires_at, last_seen_at)
            VALUES (#{tokenHash}, #{accountId}, #{gameId}, #{now}, #{expiresAt}, #{now})
            """)
    int insertSession(String tokenHash, UUID accountId, String gameId, Instant now, Instant expiresAt);

    @Select("""
            SELECT account_id, game_id, expires_at FROM account_session
            WHERE token_hash = #{tokenHash} AND revoked_at IS NULL AND expires_at > #{now}
            """)
    SessionRow findSession(String tokenHash, Instant now);

    @Update("UPDATE account_session SET revoked_at = #{now} WHERE token_hash = #{tokenHash} AND revoked_at IS NULL")
    int revokeSession(String tokenHash, Instant now);

    @Update("""
            UPDATE account_session SET last_seen_at = #{now}
            WHERE token_hash = #{tokenHash} AND revoked_at IS NULL AND expires_at > #{now}
            """)
    int touchSession(String tokenHash, Instant now);

    @Update("UPDATE account_session SET revoked_at = #{now} WHERE account_id = #{accountId} AND revoked_at IS NULL")
    int revokeAllSessions(UUID accountId, Instant now);

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

    @Update("""
            UPDATE account_wallet
            SET crystal_balance = crystal_balance + #{amount},
                version = version + 1, updated_at = #{now}
            WHERE account_id = #{accountId}
            """)
    int addCrystals(UUID accountId, long amount, Instant now);

    @Select("""
            SELECT code_hash, currency, reward_amount, max_redemptions, redeemed_count,
                   valid_from, valid_until, enabled, created_at
            FROM redemption_code WHERE code_hash = #{codeHash}
            FOR UPDATE
            """)
    RedemptionCodeRow lockRedemptionCode(@Param("codeHash") String codeHash);

    @Select("""
            SELECT code_hash, currency, reward_amount, max_redemptions, redeemed_count,
                   valid_from, valid_until, enabled, created_at
            FROM redemption_code
            ORDER BY created_at DESC, code_hash
            """)
    List<RedemptionCodeRow> listRedemptionCodes();

    @Insert("""
            INSERT INTO redemption_code
                (code_hash, currency, reward_amount, max_redemptions, redeemed_count,
                 valid_from, valid_until, enabled, created_at)
            VALUES (#{codeHash}, #{currency}, #{rewardAmount}, #{maxRedemptions}, 0,
                    #{validFrom}, #{validUntil}, TRUE, #{now})
            ON CONFLICT (code_hash) DO NOTHING
            """)
    int insertRedemptionCode(String codeHash, String currency, long rewardAmount,
                             Integer maxRedemptions, Instant validFrom, Instant validUntil,
                             Instant now);

    @Update("UPDATE redemption_code SET enabled = #{enabled} WHERE code_hash = #{codeHash}")
    int setRedemptionCodeEnabled(String codeHash, boolean enabled);

    @Select("""
            SELECT
              (SELECT COUNT(*) FROM identity_account) AS accounts,
              (SELECT COUNT(*) FROM identity_account WHERE is_admin = TRUE) AS administrators,
              (SELECT COUNT(*) FROM redemption_code WHERE enabled = TRUE) AS enabled_redemption_codes,
              (SELECT COUNT(*) FROM redemption_claim) AS redemption_claims,
              (SELECT COUNT(*) FROM mail_message) AS mail_messages,
              (SELECT COUNT(*) FROM account_mail) AS delivered_mail,
              (SELECT COUNT(*) FROM account_cosmetic) AS granted_cosmetics
            """)
    AdminOverview adminOverview();

    @Select("SELECT operation, target_key FROM admin_operation_request WHERE request_id = #{requestId}")
    AdminOperationRow findAdminOperation(@Param("requestId") String requestId);

    @Insert("""
            INSERT INTO admin_operation_request
                (request_id, admin_account_id, operation, target_key, created_at)
            VALUES (#{requestId}, #{adminAccountId}, #{operation}, #{targetKey}, #{now})
            """)
    int insertAdminOperation(String requestId, UUID adminAccountId, String operation,
                             String targetKey, Instant now);

    @Insert("""
            INSERT INTO admin_audit_log
                (audit_id, admin_account_id, action, target_key, details, created_at)
            VALUES (#{auditId}, #{adminAccountId}, #{action}, #{targetKey}, #{details}, #{now})
            """)
    int insertAdminAudit(UUID auditId, UUID adminAccountId, String action,
                         String targetKey, String details, Instant now);

    @Select("""
            SELECT f.friendship_id, f.requester_id,
                   requester.primary_game_id AS requester_game_id,
                   requester.avatar_key AS requester_avatar_key,
                   EXISTS(SELECT 1 FROM account_session s WHERE s.account_id = f.requester_id
                          AND s.revoked_at IS NULL AND s.expires_at > #{now}
                          AND s.last_seen_at >= #{onlineAfter}) AS requester_online,
                   f.addressee_id,
                   addressee.primary_game_id AS addressee_game_id,
                   addressee.avatar_key AS addressee_avatar_key,
                   EXISTS(SELECT 1 FROM account_session s WHERE s.account_id = f.addressee_id
                          AND s.revoked_at IS NULL AND s.expires_at > #{now}
                          AND s.last_seen_at >= #{onlineAfter}) AS addressee_online,
                   f.status, f.updated_at
            FROM friendship f
            JOIN identity_account requester ON requester.account_id = f.requester_id
            JOIN identity_account addressee ON addressee.account_id = f.addressee_id
            WHERE f.requester_id = #{accountId} OR f.addressee_id = #{accountId}
            ORDER BY f.updated_at DESC
            """)
    List<FriendshipViewRow> listFriendships(UUID accountId, Instant now, Instant onlineAfter);

    @Select("""
            SELECT friendship_id, requester_id, addressee_id, status
            FROM friendship WHERE friendship_id = #{friendshipId} FOR UPDATE
            """)
    FriendshipStateRow lockFriendship(@Param("friendshipId") UUID friendshipId);

    @Select("""
            SELECT friendship_id, requester_id, addressee_id, status
            FROM friendship
            WHERE (requester_id = #{firstId} AND addressee_id = #{secondId})
               OR (requester_id = #{secondId} AND addressee_id = #{firstId})
            FOR UPDATE
            """)
    FriendshipStateRow lockFriendshipPair(UUID firstId, UUID secondId);

    @Insert("""
            INSERT INTO friendship
                (friendship_id, requester_id, addressee_id, status, created_at, updated_at)
            VALUES (#{friendshipId}, #{requesterId}, #{addresseeId}, #{status}, #{now}, #{now})
            """)
    int insertFriendship(UUID friendshipId, UUID requesterId, UUID addresseeId,
                         String status, Instant now);

    @Update("UPDATE friendship SET status = 'ACCEPTED', updated_at = #{now} WHERE friendship_id = #{friendshipId} AND status = 'PENDING'")
    int acceptFriendship(UUID friendshipId, Instant now);

    @org.apache.ibatis.annotations.Delete("DELETE FROM friendship WHERE friendship_id = #{friendshipId}")
    int deleteFriendship(@Param("friendshipId") UUID friendshipId);

    @Select("""
            SELECT operation, target_key FROM friend_operation_request
            WHERE account_id = #{accountId} AND request_id = #{requestId}
            """)
    FriendOperationRow findFriendOperation(UUID accountId, String requestId);

    @Insert("""
            INSERT INTO friend_operation_request
                (account_id, request_id, operation, target_key, created_at)
            VALUES (#{accountId}, #{requestId}, #{operation}, #{targetKey}, #{now})
            """)
    int insertFriendOperation(UUID accountId, String requestId, String operation,
                              String targetKey, Instant now);

    @Select("SELECT EXISTS(SELECT 1 FROM identity_account WHERE account_id = #{accountId})")
    boolean accountExists(@Param("accountId") UUID accountId);

    @Select("""
            SELECT a.account_id, a.real_name, a.primary_game_id, a.avatar_key,
                   a.is_admin AS administrator, w.chip_balance, w.crystal_balance,
                   EXISTS(SELECT 1 FROM account_session s WHERE s.account_id = a.account_id
                          AND s.revoked_at IS NULL AND s.expires_at > #{now}
                          AND s.last_seen_at >= #{onlineAfter}) AS online
            FROM identity_account a
            JOIN account_wallet w ON w.account_id = a.account_id
            ORDER BY a.created_at, a.account_id
            """)
    List<AdminAccountRow> listAdminAccounts(Instant now, Instant onlineAfter);

    @Select("""
            SELECT f.friendship_id, f.requester_id,
                   requester.primary_game_id AS requester_game_id,
                   requester.avatar_key AS requester_avatar_key,
                   FALSE AS requester_online,
                   f.addressee_id,
                   addressee.primary_game_id AS addressee_game_id,
                   addressee.avatar_key AS addressee_avatar_key,
                   FALSE AS addressee_online,
                   f.status, f.updated_at
            FROM friendship f
            JOIN identity_account requester ON requester.account_id = f.requester_id
            JOIN identity_account addressee ON addressee.account_id = f.addressee_id
            ORDER BY f.updated_at DESC
            """)
    List<FriendshipViewRow> listAllFriendships();

    @Update("""
            UPDATE account_wallet
            SET chip_balance = chip_balance + #{chipDelta},
                crystal_balance = crystal_balance + #{crystalDelta},
                version = version + 1, updated_at = #{now}
            WHERE account_id = #{accountId}
              AND chip_balance + #{chipDelta} >= 0
              AND crystal_balance + #{crystalDelta} >= 0
            """)
    int adjustWallet(UUID accountId, long chipDelta, long crystalDelta, Instant now);

    @Update("UPDATE identity_account SET password_hash = #{passwordHash}, updated_at = #{now} WHERE account_id = #{accountId}")
    int updatePassword(UUID accountId, String passwordHash, Instant now);

    @Select("""
            SELECT currency, reward_amount FROM redemption_claim
            WHERE account_id = #{accountId} AND request_id = #{requestId}
            """)
    RedemptionClaimRow findRedemptionClaimByRequest(UUID accountId, String requestId);

    @Select("""
            SELECT currency, reward_amount FROM redemption_claim
            WHERE account_id = #{accountId} AND code_hash = #{codeHash}
            """)
    RedemptionClaimRow findRedemptionClaimByCode(UUID accountId, String codeHash);

    @Insert("""
            INSERT INTO redemption_claim
                (code_hash, account_id, request_id, currency, reward_amount, claimed_at)
            VALUES (#{codeHash}, #{accountId}, #{requestId}, #{currency}, #{rewardAmount}, #{now})
            """)
    int insertRedemptionClaim(String codeHash, UUID accountId, String requestId,
                              String currency, long rewardAmount, Instant now);

    @Update("""
            UPDATE redemption_code SET redeemed_count = redeemed_count + 1
            WHERE code_hash = #{codeHash}
            """)
    int incrementRedemptionCount(@Param("codeHash") String codeHash);

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

    @Select("""
            SELECT escrow_id, account_id, game_id, room_id, buy_in, returned_chips, status
            FROM table_buy_in
            WHERE room_id = #{roomId} AND account_id = #{accountId}
            ORDER BY created_at DESC LIMIT 1
            """)
    TableEscrowRow findLatestEscrow(UUID accountId, String roomId);

    @Select("""
            SELECT m.mail_id, m.type, m.subject, m.body, m.reward_chips, m.reward_crystals,
                   m.reward_skin_key, m.created_at, am.read_at, am.claimed_at
            FROM account_mail am JOIN mail_message m ON m.mail_id = am.mail_id
            WHERE am.account_id = #{accountId}
            ORDER BY m.created_at DESC LIMIT 100
            """)
    List<MailRow> listMail(@Param("accountId") UUID accountId);

    @Select("SELECT COUNT(*) FROM account_mail WHERE account_id = #{accountId} AND read_at IS NULL")
    long unreadMailCount(@Param("accountId") UUID accountId);

    @Select("""
            SELECT m.mail_id, m.type, m.subject, m.body, m.reward_chips, m.reward_crystals,
                   m.reward_skin_key, m.created_at, am.read_at, am.claimed_at
            FROM account_mail am JOIN mail_message m ON m.mail_id = am.mail_id
            WHERE am.account_id = #{accountId} AND am.mail_id = #{mailId}
            """)
    MailRow findMail(UUID accountId, UUID mailId);

    @Select("""
            SELECT m.mail_id, m.type, m.subject, m.body, m.reward_chips, m.reward_crystals,
                   m.reward_skin_key, m.created_at, am.read_at, am.claimed_at
            FROM account_mail am JOIN mail_message m ON m.mail_id = am.mail_id
            WHERE am.account_id = #{accountId} AND am.mail_id = #{mailId}
            FOR UPDATE OF am
            """)
    MailRow lockMail(UUID accountId, UUID mailId);

    @Update("UPDATE account_mail SET read_at = COALESCE(read_at, #{now}) WHERE account_id = #{accountId} AND mail_id = #{mailId}")
    int markMailRead(UUID accountId, UUID mailId, Instant now);

    @Update("""
            UPDATE account_mail SET read_at = COALESCE(read_at, #{now}), claimed_at = #{now},
                claim_request_id = #{requestId}
            WHERE account_id = #{accountId} AND mail_id = #{mailId} AND claimed_at IS NULL
            """)
    int markMailClaimed(UUID accountId, UUID mailId, String requestId, Instant now);

    @Insert("""
            INSERT INTO account_cosmetic (account_id, skin_key, acquired_at, source_mail_id)
            VALUES (#{accountId}, #{skinKey}, #{now}, #{mailId})
            ON CONFLICT (account_id, skin_key) DO NOTHING
            """)
    int grantCosmetic(UUID accountId, String skinKey, Instant now, UUID mailId);

    @Insert("""
            INSERT INTO mail_message
                (mail_id, request_id, sender_account_id, type, subject, body,
                 reward_chips, reward_crystals, reward_skin_key, created_at)
            VALUES (#{mailId}, #{requestId}, #{senderAccountId}, #{type}, #{subject}, #{body},
                    #{rewardChips}, #{rewardCrystals}, #{rewardSkinKey}, #{now})
            ON CONFLICT (request_id) DO NOTHING
            """)
    int insertMailMessage(UUID mailId, String requestId, UUID senderAccountId, String type,
                          String subject, String body, long rewardChips, long rewardCrystals,
                          String rewardSkinKey, Instant now);

    @Select("""
            SELECT m.mail_id, m.type, m.subject, m.body, m.reward_chips, m.reward_crystals,
                   m.reward_skin_key, m.created_at, NULL AS read_at, NULL AS claimed_at
            FROM mail_message m WHERE m.request_id = #{requestId}
            """)
    MailRow findMailByRequest(@Param("requestId") String requestId);

    @Insert("""
            INSERT INTO account_mail (mail_id, account_id)
            SELECT #{mailId}, account_id FROM identity_account
            ON CONFLICT DO NOTHING
            """)
    int distributeMailToAll(@Param("mailId") UUID mailId);

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
