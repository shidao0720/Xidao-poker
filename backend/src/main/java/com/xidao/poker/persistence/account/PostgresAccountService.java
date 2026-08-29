package com.xidao.poker.persistence.account;

import com.xidao.poker.application.account.*;
import com.xidao.poker.persistence.history.HistorySchemaInitializer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.*;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

public class PostgresAccountService implements AccountService, TableEconomyService {
    public static final long INITIAL_CHIPS = 10_000;
    public static final long DAILY_CHIPS = 500;
    public static final long CHIPS_PER_CRYSTAL = 10;
    private static final Duration SESSION_TTL = Duration.ofDays(7);
    private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    private final AccountMapper mapper;
    private final HistorySchemaInitializer schema;
    private final PasswordEncoder passwords;
    private final SecureRandom random;
    private final Clock clock;
    private final ZoneId checkInZone;

    public PostgresAccountService(AccountMapper mapper, HistorySchemaInitializer schema,
                                  PasswordEncoder passwords, SecureRandom random,
                                  Clock clock, ZoneId checkInZone) {
        this.mapper = mapper;
        this.schema = schema;
        this.passwords = passwords;
        this.random = random;
        this.clock = clock;
        this.checkInZone = checkInZone;
    }

    @Override
    @Transactional(transactionManager = "historyTransactionManager")
    public AuthResult register(String realName, String gameId, String password) {
        schema.ensureReady();
        String cleanName = cleanRealName(realName);
        String nameKey = nameKey(cleanName);
        String cleanGameId = cleanGameId(gameId);
        validatePassword(password);
        Instant now = clock.instant();

        AccountRow account = mapper.lockByRealNameKey(nameKey);
        boolean created = account == null;
        UUID accountId;
        if (created) {
            accountId = UUID.randomUUID();
            String passwordHash = passwords.encode(password);
            mapper.insertAccount(accountId, cleanName, nameKey, passwordHash, now);
            mapper.insertWallet(accountId, now);
            mapper.insertLedger(UUID.randomUUID(), accountId, "CHIP", INITIAL_CHIPS,
                    INITIAL_CHIPS, "ACCOUNT_CREATED", "account-created", now);
        } else {
            if (!passwords.matches(password, account.passwordHash())) invalidCredentials();
            accountId = account.accountId();
        }

        int inserted = mapper.insertGameId(cleanGameId, accountId, now);
        if (inserted == 0 && mapper.attachExistingGameId(cleanGameId, accountId, now) == 0) {
            throw new AccountException(AccountErrorCode.GAME_ID_TAKEN, "game ID already belongs to another identity");
        }
        mapper.setPrimaryGameId(accountId, cleanGameId, now);
        return issueSession(accountId, cleanGameId, now);
    }

    @Override
    @Transactional(transactionManager = "historyTransactionManager")
    public AuthResult login(String realName, String gameId, String password) {
        schema.ensureReady();
        String cleanGameId = cleanGameId(gameId);
        AccountRow account = mapper.findByGameId(cleanGameId);
        if (account == null
                || !account.realNameKey().equals(nameKey(cleanRealName(realName)))
                || !passwords.matches(password, account.passwordHash())) {
            invalidCredentials();
        }
        return issueSession(account.accountId(), cleanGameId, clock.instant());
    }

    @Override
    @Transactional(transactionManager = "historyTransactionManager")
    public void logout(String sessionToken) {
        schema.ensureReady();
        mapper.revokeSession(hashToken(requireToken(sessionToken)), clock.instant());
    }

    @Override
    public AccountPrincipal authenticate(String sessionToken) {
        schema.ensureReady();
        SessionRow session = mapper.findSession(hashToken(requireToken(sessionToken)), clock.instant());
        if (session == null) throw new AccountException(AccountErrorCode.UNAUTHORIZED, "session is invalid or expired");
        return new AccountPrincipal(session.accountId(), session.gameId());
    }

    @Override
    public AccountProfile profile(String sessionToken) {
        AccountPrincipal principal = authenticate(sessionToken);
        return profile(principal.accountId(), principal.gameId());
    }

    @Override
    @Transactional(transactionManager = "historyTransactionManager")
    public AccountProfile updateAvatar(String sessionToken, String avatarKey) {
        AccountPrincipal principal = authenticate(sessionToken);
        String normalized = AvatarCatalog.normalize(avatarKey);
        mapper.updateAvatar(principal.accountId(), normalized, clock.instant());
        return profile(principal.accountId(), principal.gameId());
    }

    @Override
    @Transactional(transactionManager = "historyTransactionManager")
    public CheckInResult checkIn(String sessionToken, String requestId) {
        AccountPrincipal principal = authenticate(sessionToken);
        validateRequestId(requestId);
        mapper.lockWallet(principal.accountId());
        Instant now = clock.instant();
        LocalDate date = LocalDate.ofInstant(now, checkInZone);
        boolean awarded = mapper.insertCheckIn(principal.accountId(), date, requestId, DAILY_CHIPS, now) == 1;
        if (awarded) {
            mapper.addChips(principal.accountId(), DAILY_CHIPS, now);
            long chips = mapper.chipBalance(principal.accountId());
            mapper.insertLedger(UUID.randomUUID(), principal.accountId(), "CHIP", DAILY_CHIPS,
                    chips, "DAILY_CHECK_IN", requestId, now);
        }
        WalletSnapshot wallet = wallet(principal.accountId());
        return new CheckInResult(awarded, awarded ? DAILY_CHIPS : 0, date, wallet);
    }

    @Override
    @Transactional(transactionManager = "historyTransactionManager")
    public WalletSnapshot exchangeForCrystals(String sessionToken, String requestId, long chips) {
        AccountPrincipal principal = authenticate(sessionToken);
        validateRequestId(requestId);
        if (chips <= 0 || chips % CHIPS_PER_CRYSTAL != 0) {
            throw new AccountException(AccountErrorCode.INVALID_EXCHANGE,
                    "chips must be a positive multiple of 10");
        }
        mapper.lockWallet(principal.accountId());
        if (mapper.ledgerRequestExists(principal.accountId(), requestId)) return wallet(principal.accountId());
        long crystals = chips / CHIPS_PER_CRYSTAL;
        Instant now = clock.instant();
        if (mapper.convertToCrystals(principal.accountId(), chips, crystals, now) != 1) {
            throw new AccountException(AccountErrorCode.INSUFFICIENT_CHIPS, "insufficient available chips");
        }
        WalletSnapshot wallet = wallet(principal.accountId());
        mapper.insertLedger(UUID.randomUUID(), principal.accountId(), "CHIP", -chips,
                wallet.chips(), "EXCHANGE_TO_CRYSTAL", requestId, now);
        mapper.insertLedger(UUID.randomUUID(), principal.accountId(), "CRYSTAL", crystals,
                wallet.spiritCrystals(), "EXCHANGE_FROM_CHIP", requestId, now);
        return wallet;
    }

    @Override
    public Leaderboards leaderboards() {
        schema.ensureReady();
        return new Leaderboards(rank(mapper.topHandsWon()), rank(mapper.topTotalWinnings()),
                rank(mapper.topSingleHandGain()));
    }

    @Override
    @Transactional(transactionManager = "historyTransactionManager")
    public TableBuyInReservation reserveBuyIn(
            UUID accountId,
            String gameId,
            String roomId,
            long buyIn,
            String requestId
    ) {
        schema.ensureReady();
        if (accountId == null) throw new AccountException(AccountErrorCode.UNAUTHORIZED, "account is required");
        String cleanGameId = cleanGameId(gameId);
        validateRoomId(roomId);
        validateRequestId(requestId);
        if (buyIn <= 0 || buyIn > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("table buy-in is invalid");
        }
        AccountRow owner = mapper.findByGameId(cleanGameId);
        if (owner == null || !owner.accountId().equals(accountId)) {
            throw new AccountException(AccountErrorCode.UNAUTHORIZED, "game ID does not belong to this account");
        }

        mapper.lockWallet(accountId);
        TableEscrowRow active = mapper.lockActiveEscrow(accountId);
        if (active != null) {
            if (active.roomId().equals(roomId) && active.gameId().equals(cleanGameId)) {
                return new TableBuyInReservation(false, active.buyIn(), wallet(accountId));
            }
            throw new AccountException(AccountErrorCode.ALREADY_AT_TABLE,
                    "this identity already has chips reserved at another table");
        }

        Instant now = clock.instant();
        if (mapper.debitBuyIn(accountId, buyIn, now) != 1) {
            throw new AccountException(AccountErrorCode.INSUFFICIENT_CHIPS, "insufficient chips for table buy-in");
        }
        UUID escrowId = UUID.randomUUID();
        mapper.insertEscrow(escrowId, accountId, cleanGameId, roomId, buyIn, requestId, now);
        WalletSnapshot wallet = wallet(accountId);
        mapper.insertLedger(UUID.randomUUID(), accountId, "CHIP", -buyIn, wallet.chips(),
                "TABLE_BUY_IN", requestId, now);
        return new TableBuyInReservation(true, buyIn, wallet);
    }

    @Override
    @Transactional(transactionManager = "historyTransactionManager")
    public WalletSnapshot settleSeat(String roomId, String gameId, long finalStack, String requestId) {
        schema.ensureReady();
        validateRoomId(roomId);
        String cleanGameId = cleanGameId(gameId);
        validateRequestId(requestId);
        if (finalStack < 0 || finalStack > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("final table stack is invalid");
        }
        TableEscrowRow alreadySettled = mapper.findEscrowBySettlementRequest(requestId);
        if (alreadySettled != null) return wallet(alreadySettled.accountId());
        TableEscrowRow located = mapper.findActiveEscrowBySeat(roomId, cleanGameId);
        if (located == null) {
            throw new IllegalStateException("table escrow is missing for settled seat");
        }
        mapper.lockWallet(located.accountId());
        TableEscrowRow escrow = mapper.lockActiveEscrowBySeat(roomId, cleanGameId);
        if (escrow == null) throw new IllegalStateException("table escrow disappeared during settlement");
        Instant now = clock.instant();
        if (mapper.settleEscrow(escrow.escrowId(), finalStack, requestId, now) != 1) {
            return wallet(escrow.accountId());
        }
        mapper.addChips(escrow.accountId(), finalStack, now);
        WalletSnapshot wallet = wallet(escrow.accountId());
        mapper.insertLedger(UUID.randomUUID(), escrow.accountId(), "CHIP", finalStack,
                wallet.chips(), "TABLE_CASH_OUT", requestId, now);
        return wallet;
    }

    private AuthResult issueSession(UUID accountId, String gameId, Instant now) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = now.plus(SESSION_TTL);
        mapper.insertSession(hashToken(token), accountId, gameId, now, expiresAt);
        return new AuthResult(token, expiresAt, profile(accountId, gameId));
    }

    private AccountProfile profile(UUID accountId, String gameId) {
        AccountRow account = mapper.findByGameId(gameId);
        return new AccountProfile(gameId, mapper.listGameIds(accountId), wallet(accountId),
                account == null ? AvatarCatalog.DEFAULT : account.avatarKey());
    }

    private WalletSnapshot wallet(UUID accountId) {
        return new WalletSnapshot(mapper.chipBalance(accountId), mapper.crystalBalance(accountId));
    }

    private static List<LeaderboardEntry> rank(List<RankingRow> rows) {
        java.util.concurrent.atomic.AtomicInteger position = new java.util.concurrent.atomic.AtomicInteger();
        return rows.stream().map(row -> new LeaderboardEntry(position.incrementAndGet(), row.gameId(), row.value())).toList();
    }

    private static String cleanRealName(String value) {
        if (value == null) throw new IllegalArgumentException("real name is required");
        String clean = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC);
        if (clean.length() < 2 || clean.length() > 64) throw new IllegalArgumentException("real name length is invalid");
        return clean;
    }

    private static String nameKey(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    private static String cleanGameId(String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty() || clean.length() > 12) throw new IllegalArgumentException("game ID is invalid");
        return clean;
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 72) {
            throw new IllegalArgumentException("password length must be between 8 and 72");
        }
    }

    private static void validateRequestId(String requestId) {
        if (requestId == null || !REQUEST_ID.matcher(requestId).matches()) {
            throw new IllegalArgumentException("request ID is invalid");
        }
    }

    private static void validateRoomId(String roomId) {
        if (roomId == null || !roomId.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("room ID is invalid");
        }
    }

    private static String requireToken(String token) {
        if (token == null || token.length() < 32 || token.length() > 128) {
            throw new AccountException(AccountErrorCode.UNAUTHORIZED, "session is invalid or expired");
        }
        return token;
    }

    private static String hashToken(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static void invalidCredentials() {
        throw new AccountException(AccountErrorCode.INVALID_CREDENTIALS, "identity credentials are invalid");
    }
}
