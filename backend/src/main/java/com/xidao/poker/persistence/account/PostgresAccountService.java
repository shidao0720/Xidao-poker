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
    private static final Duration PRESENCE_TTL = Duration.ofSeconds(45);
    private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9_-]{8,64}");
    private static final Pattern SKIN_KEY = Pattern.compile("[a-z0-9_-]{1,64}");

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
            mapper.promoteIfNoAdmin(accountId, now);
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
    @Transactional(transactionManager = "historyTransactionManager")
    public void heartbeat(String sessionToken, String requestId) {
        authenticate(sessionToken);
        validateRequestId(requestId);
        mapper.touchSession(hashToken(requireToken(sessionToken)), clock.instant());
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
    @Transactional(transactionManager = "historyTransactionManager")
    public RedemptionResult redeemCode(String sessionToken, String requestId, String code) {
        AccountPrincipal principal = authenticate(sessionToken);
        validateRequestId(requestId);
        String normalizedCode = RedemptionCodeCodec.normalize(code);
        String codeHash = RedemptionCodeCodec.hash(normalizedCode);
        mapper.lockWallet(principal.accountId());

        RedemptionClaimRow repeatedRequest = mapper.findRedemptionClaimByRequest(
                principal.accountId(), requestId);
        if (repeatedRequest != null) {
            return new RedemptionResult(repeatedRequest.currency(), repeatedRequest.rewardAmount(),
                    wallet(principal.accountId()));
        }

        RedemptionCodeRow offer = mapper.lockRedemptionCode(codeHash);
        Instant now = clock.instant();
        if (offer == null) {
            throw new AccountException(AccountErrorCode.INVALID_REDEMPTION_CODE,
                    "redemption code is invalid");
        }
        if (!offer.enabled()
                || now.isBefore(offer.validFrom())
                || (offer.validUntil() != null && now.isAfter(offer.validUntil()))
                || (offer.maxRedemptions() != null && offer.redeemedCount() >= offer.maxRedemptions())) {
            throw new AccountException(AccountErrorCode.REDEMPTION_UNAVAILABLE,
                    "redemption code is unavailable");
        }
        if (mapper.findRedemptionClaimByCode(principal.accountId(), codeHash) != null) {
            throw new AccountException(AccountErrorCode.REDEMPTION_ALREADY_USED,
                    "redemption code has already been used by this identity");
        }

        mapper.insertRedemptionClaim(codeHash, principal.accountId(), requestId,
                offer.currency(), offer.rewardAmount(), now);
        mapper.incrementRedemptionCount(codeHash);
        if ("CHIP".equals(offer.currency())) {
            mapper.addChips(principal.accountId(), offer.rewardAmount(), now);
        } else if ("CRYSTAL".equals(offer.currency())) {
            mapper.addCrystals(principal.accountId(), offer.rewardAmount(), now);
        } else {
            throw new IllegalStateException("unsupported redemption currency");
        }
        WalletSnapshot wallet = wallet(principal.accountId());
        long balanceAfter = "CHIP".equals(offer.currency()) ? wallet.chips() : wallet.spiritCrystals();
        mapper.insertLedger(UUID.randomUUID(), principal.accountId(), offer.currency(), offer.rewardAmount(),
                balanceAfter, "REDEMPTION_CODE", requestId, now);
        return new RedemptionResult(offer.currency(), offer.rewardAmount(), wallet);
    }

    @Override
    public TableSessionResult tableSessionResult(String sessionToken, String roomId) {
        AccountPrincipal principal = authenticate(sessionToken);
        validateRoomId(roomId);
        TableEscrowRow escrow = mapper.findLatestEscrow(principal.accountId(), roomId);
        if (escrow == null) {
            throw new AccountException(AccountErrorCode.TABLE_RESULT_NOT_FOUND,
                    "table settlement was not found");
        }
        if ("ACTIVE".equals(escrow.status())) return TableSessionResult.active(escrow.buyIn());
        long returned = escrow.returnedChips() == null ? 0 : escrow.returnedChips();
        return TableSessionResult.settled(escrow.buyIn(), returned);
    }

    @Override
    public MailInbox inbox(String sessionToken) {
        AccountPrincipal principal = authenticate(sessionToken);
        return new MailInbox(mapper.listMail(principal.accountId()).stream()
                .map(PostgresAccountService::mailItem).toList(),
                mapper.unreadMailCount(principal.accountId()));
    }

    @Override
    @Transactional(transactionManager = "historyTransactionManager")
    public MailItem markMailRead(String sessionToken, UUID mailId) {
        AccountPrincipal principal = authenticate(sessionToken);
        if (mailId == null || mapper.markMailRead(principal.accountId(), mailId, clock.instant()) != 1) {
            throw new AccountException(AccountErrorCode.MAIL_NOT_FOUND, "mail was not found");
        }
        return mailItem(mapper.findMail(principal.accountId(), mailId));
    }

    @Override
    @Transactional(transactionManager = "historyTransactionManager")
    public MailClaimResult claimMail(String sessionToken, UUID mailId, String requestId) {
        AccountPrincipal principal = authenticate(sessionToken);
        validateRequestId(requestId);
        mapper.lockWallet(principal.accountId());
        MailRow row = mapper.lockMail(principal.accountId(), mailId);
        if (row == null) throw new AccountException(AccountErrorCode.MAIL_NOT_FOUND, "mail was not found");
        MailItem current = mailItem(row);
        if (!current.hasAttachment()) {
            throw new AccountException(AccountErrorCode.INVALID_MAIL, "mail has no attachment");
        }
        if (!current.claimed()) {
            Instant now = clock.instant();
            if (row.rewardChips() > 0) mapper.addChips(principal.accountId(), row.rewardChips(), now);
            if (row.rewardCrystals() > 0) mapper.addCrystals(principal.accountId(), row.rewardCrystals(), now);
            if (row.rewardSkinKey() != null) {
                mapper.grantCosmetic(principal.accountId(), row.rewardSkinKey(), now, mailId);
            }
            if (mapper.markMailClaimed(principal.accountId(), mailId, requestId, now) != 1) {
                throw new IllegalStateException("mail claim state changed unexpectedly");
            }
            WalletSnapshot wallet = wallet(principal.accountId());
            if (row.rewardChips() > 0) {
                mapper.insertLedger(UUID.randomUUID(), principal.accountId(), "CHIP", row.rewardChips(),
                        wallet.chips(), "MAIL_REWARD", requestId, now);
            }
            if (row.rewardCrystals() > 0) {
                mapper.insertLedger(UUID.randomUUID(), principal.accountId(), "CRYSTAL", row.rewardCrystals(),
                        wallet.spiritCrystals(), "MAIL_REWARD", requestId, now);
            }
        }
        return new MailClaimResult(mailItem(mapper.findMail(principal.accountId(), mailId)),
                wallet(principal.accountId()), mapper.listCosmetics(principal.accountId()));
    }

    @Override
    @Transactional(transactionManager = "historyTransactionManager")
    public MailItem broadcastMail(String sessionToken, String requestId, BroadcastMailCommand command) {
        AccountPrincipal principal = authenticate(sessionToken);
        validateRequestId(requestId);
        requireAdmin(principal);
        BroadcastMailCommand clean = validateMail(command);
        UUID mailId = UUID.randomUUID();
        Instant now = clock.instant();
        mapper.insertMailMessage(mailId, requestId, principal.accountId(), clean.type(),
                clean.subject(), clean.body(), clean.rewardChips(), clean.rewardCrystals(),
                clean.rewardSkinKey(), now);
        MailRow row = mapper.findMailByRequest(requestId);
        if (row == null) throw new IllegalStateException("mail broadcast could not be created");
        mapper.distributeMailToAll(row.mailId());
        return mailItem(row);
    }

    @Override
    public AdminOverview adminOverview(String sessionToken) {
        AccountPrincipal principal = authenticate(sessionToken);
        requireAdmin(principal);
        return mapper.adminOverview();
    }

    @Override
    public List<AdminRedemptionCode> adminRedemptionCodes(String sessionToken) {
        AccountPrincipal principal = authenticate(sessionToken);
        requireAdmin(principal);
        return mapper.listRedemptionCodes().stream().map(PostgresAccountService::adminCode).toList();
    }

    @Override
    @Transactional(transactionManager = "historyTransactionManager")
    public AdminRedemptionCodeCreated createRedemptionCode(
            String sessionToken,
            String requestId,
            CreateRedemptionCodeCommand command
    ) {
        AccountPrincipal principal = authenticate(sessionToken);
        requireAdmin(principal);
        validateRequestId(requestId);
        if (command == null) throw new IllegalArgumentException("redemption code is required");

        String normalizedCode = RedemptionCodeCodec.normalize(command.code());
        String codeHash = RedemptionCodeCodec.hash(normalizedCode);
        String currency = command.currency() == null ? "" : command.currency().trim().toUpperCase(Locale.ROOT);
        if (!List.of("CHIP", "CRYSTAL").contains(currency)) {
            throw new IllegalArgumentException("redemption currency is invalid");
        }
        if (command.rewardAmount() <= 0 || command.rewardAmount() > 10_000_000) {
            throw new IllegalArgumentException("redemption reward amount is invalid");
        }
        if (command.maxRedemptions() != null
                && (command.maxRedemptions() <= 0 || command.maxRedemptions() > 1_000_000)) {
            throw new IllegalArgumentException("redemption limit is invalid");
        }
        Instant now = clock.instant();
        Instant validFrom = command.validFrom() == null ? now : command.validFrom();
        if (command.validUntil() != null && !command.validUntil().isAfter(validFrom)) {
            throw new IllegalArgumentException("redemption expiry must be after its start time");
        }

        String target = codeHash;
        AdminOperationRow repeated = mapper.findAdminOperation(requestId);
        if (repeated != null) {
            requireSameAdminOperation(repeated, "CREATE_REDEMPTION", target);
            RedemptionCodeRow existing = mapper.lockRedemptionCode(codeHash);
            if (existing == null) throw new IllegalStateException("idempotent redemption code is missing");
            return new AdminRedemptionCodeCreated(normalizedCode, adminCode(existing));
        }
        if (mapper.insertRedemptionCode(codeHash, currency, command.rewardAmount(),
                command.maxRedemptions(), validFrom, command.validUntil(), now) != 1) {
            throw new AccountException(AccountErrorCode.REDEMPTION_CODE_EXISTS,
                    "redemption code already exists");
        }
        mapper.insertAdminOperation(requestId, principal.accountId(), "CREATE_REDEMPTION", target, now);
        return new AdminRedemptionCodeCreated(normalizedCode, adminCode(mapper.lockRedemptionCode(codeHash)));
    }

    @Override
    @Transactional(transactionManager = "historyTransactionManager")
    public AdminRedemptionCode setRedemptionCodeEnabled(
            String sessionToken,
            String requestId,
            String codeHash,
            boolean enabled
    ) {
        AccountPrincipal principal = authenticate(sessionToken);
        requireAdmin(principal);
        validateRequestId(requestId);
        String cleanHash = codeHash == null ? "" : codeHash.trim().toLowerCase(Locale.ROOT);
        if (!cleanHash.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("redemption code hash is invalid");
        String target = cleanHash + ":" + enabled;
        AdminOperationRow repeated = mapper.findAdminOperation(requestId);
        if (repeated != null) {
            requireSameAdminOperation(repeated, "SET_REDEMPTION", target);
            RedemptionCodeRow existing = mapper.lockRedemptionCode(cleanHash);
            if (existing == null) throw new AccountException(AccountErrorCode.INVALID_REDEMPTION_CODE,
                    "redemption code was not found");
            return adminCode(existing);
        }
        if (mapper.setRedemptionCodeEnabled(cleanHash, enabled) != 1) {
            throw new AccountException(AccountErrorCode.INVALID_REDEMPTION_CODE,
                    "redemption code was not found");
        }
        mapper.insertAdminOperation(requestId, principal.accountId(), "SET_REDEMPTION", target, clock.instant());
        return adminCode(mapper.lockRedemptionCode(cleanHash));
    }

    @Override
    public List<StoreItem> storeCatalog(String sessionToken) {
        authenticate(sessionToken);
        return CosmeticCatalog.items();
    }

    @Override
    @Transactional(transactionManager = "historyTransactionManager")
    public StorePurchaseResult purchaseCosmetic(String sessionToken, String requestId, String itemKey) {
        AccountPrincipal principal = authenticate(sessionToken);
        validateRequestId(requestId);
        StoreItem requested = CosmeticCatalog.require(itemKey);
        mapper.lockWallet(principal.accountId());

        CosmeticPurchaseRow repeated = mapper.findCosmeticPurchaseByRequest(principal.accountId(), requestId);
        if (repeated != null) {
            if (!repeated.catalogKey().equals(requested.key())) {
                throw new AccountException(AccountErrorCode.STORE_REQUEST_CONFLICT,
                        "request ID was already used for another store item");
            }
            return purchaseResult(principal.accountId(), requested);
        }

        List<String> owned = mapper.listCosmetics(principal.accountId());
        if (requested.grants().stream().allMatch(owned::contains)) {
            throw new AccountException(AccountErrorCode.COSMETIC_ALREADY_OWNED,
                    "all cosmetics in this item are already owned");
        }

        Instant now = clock.instant();
        if (mapper.debitCrystals(principal.accountId(), requested.priceCrystals(), now) != 1) {
            throw new AccountException(AccountErrorCode.INSUFFICIENT_CRYSTALS,
                    "insufficient spirit crystals");
        }
        UUID purchaseId = UUID.randomUUID();
        mapper.insertCosmeticPurchase(purchaseId, principal.accountId(), requested.key(),
                requested.priceCrystals(), requestId, now);
        requested.grants().forEach(grant ->
                mapper.grantPurchasedCosmetic(principal.accountId(), grant, now, purchaseId));

        WalletSnapshot wallet = wallet(principal.accountId());
        mapper.insertLedger(UUID.randomUUID(), principal.accountId(), "CRYSTAL",
                -requested.priceCrystals(), wallet.spiritCrystals(), "STORE_PURCHASE", requestId, now);
        return purchaseResult(principal.accountId(), requested);
    }

    @Override
    @Transactional(transactionManager = "historyTransactionManager")
    public AccountProfile equipCosmetic(String sessionToken, String requestId,
                                        CosmeticSlot slot, String itemKey) {
        AccountPrincipal principal = authenticate(sessionToken);
        validateRequestId(requestId);
        if (slot == null) throw new AccountException(AccountErrorCode.INVALID_COSMETIC, "cosmetic slot is required");
        if (itemKey == null || itemKey.isBlank()) {
            mapper.unequipCosmetic(principal.accountId(), slot.name());
            return profile(principal.accountId(), principal.gameId());
        }

        StoreItem item = CosmeticCatalog.require(itemKey);
        if (CosmeticCatalog.slot(item) != slot) {
            throw new AccountException(AccountErrorCode.INVALID_COSMETIC,
                    "cosmetic does not belong to this loadout slot");
        }
        if (!mapper.ownsCosmetic(principal.accountId(), item.key())) {
            throw new AccountException(AccountErrorCode.INVALID_COSMETIC,
                    "cosmetic is not owned by this account");
        }
        mapper.equipCosmetic(principal.accountId(), slot.name(), item.key(), clock.instant());
        return profile(principal.accountId(), principal.gameId());
    }

    @Override
    public FriendDashboard friends(String sessionToken) {
        AccountPrincipal principal = authenticate(sessionToken);
        return friendDashboard(principal.accountId());
    }

    @Override
    @Transactional(transactionManager = "historyTransactionManager")
    public FriendDashboard sendFriendRequest(String sessionToken, String requestId, String gameId) {
        AccountPrincipal principal = authenticate(sessionToken);
        validateRequestId(requestId);
        AccountRow target = mapper.findByGameId(cleanGameId(gameId));
        if (target == null) throw new AccountException(AccountErrorCode.ACCOUNT_NOT_FOUND, "player was not found");
        if (target.accountId().equals(principal.accountId())) {
            throw new AccountException(AccountErrorCode.INVALID_FRIENDSHIP, "you cannot add yourself as a friend");
        }
        String targetKey = target.accountId().toString();
        FriendOperationRow repeated = mapper.findFriendOperation(principal.accountId(), requestId);
        if (repeated != null) {
            requireSameFriendOperation(repeated, "SEND", targetKey);
            return friendDashboard(principal.accountId());
        }
        if (mapper.lockFriendshipPair(principal.accountId(), target.accountId()) != null) {
            throw new AccountException(AccountErrorCode.FRIENDSHIP_EXISTS,
                    "a friendship or pending request already exists");
        }
        Instant now = clock.instant();
        mapper.insertFriendship(UUID.randomUUID(), principal.accountId(), target.accountId(), "PENDING", now);
        mapper.insertFriendOperation(principal.accountId(), requestId, "SEND", targetKey, now);
        return friendDashboard(principal.accountId());
    }

    @Override
    @Transactional(transactionManager = "historyTransactionManager")
    public FriendDashboard acceptFriendRequest(String sessionToken, String requestId, UUID friendshipId) {
        return mutateFriendship(sessionToken, requestId, friendshipId, "ACCEPT");
    }

    @Override
    @Transactional(transactionManager = "historyTransactionManager")
    public FriendDashboard rejectFriendRequest(String sessionToken, String requestId, UUID friendshipId) {
        return mutateFriendship(sessionToken, requestId, friendshipId, "REJECT");
    }

    @Override
    @Transactional(transactionManager = "historyTransactionManager")
    public FriendDashboard removeFriend(String sessionToken, String requestId, UUID friendshipId) {
        return mutateFriendship(sessionToken, requestId, friendshipId, "REMOVE");
    }

    @Override
    public List<AdminAccountView> adminAccounts(String sessionToken) {
        AccountPrincipal principal = authenticate(sessionToken);
        requireAdmin(principal);
        Instant now = clock.instant();
        return mapper.listAdminAccounts(now, now.minus(PRESENCE_TTL)).stream()
                .map(row -> new AdminAccountView(row.accountId(), row.realName(), row.primaryGameId(),
                        mapper.listGameIds(row.accountId()), row.avatarKey(), row.administrator(),
                        new WalletSnapshot(row.chipBalance(), row.crystalBalance()), row.online(),
                        "BCRYPT_PROTECTED"))
                .toList();
    }

    @Override
    public List<AdminFriendshipView> adminFriendships(String sessionToken) {
        AccountPrincipal principal = authenticate(sessionToken);
        requireAdmin(principal);
        return mapper.listAllFriendships().stream().map(PostgresAccountService::adminFriendship).toList();
    }

    @Override
    @Transactional(transactionManager = "historyTransactionManager")
    public AdminWalletAdjustment adjustAccountWallet(
            String sessionToken, String requestId, UUID accountId,
            long chipDelta, long crystalDelta, String reason
    ) {
        AccountPrincipal principal = authenticate(sessionToken);
        requireAdmin(principal);
        validateRequestId(requestId);
        if (accountId == null || !mapper.accountExists(accountId)) {
            throw new AccountException(AccountErrorCode.ACCOUNT_NOT_FOUND, "account was not found");
        }
        String note = reason == null ? "" : reason.trim();
        if ((chipDelta == 0 && crystalDelta == 0)
                || chipDelta < -10_000_000L || chipDelta > 10_000_000L
                || crystalDelta < -1_000_000L || crystalDelta > 1_000_000L
                || note.length() < 2 || note.length() > 200) {
            throw new AccountException(AccountErrorCode.INVALID_ADMIN_ADJUSTMENT,
                    "wallet adjustment or reason is invalid");
        }
        String targetKey = accountId + ":" + chipDelta + ":" + crystalDelta;
        AdminOperationRow repeated = mapper.findAdminOperation(requestId);
        if (repeated != null) {
            requireSameAdminOperation(repeated, "ADJUST_WALLET", targetKey);
            return new AdminWalletAdjustment(accountId, wallet(accountId));
        }
        if (mapper.ledgerRequestExists(accountId, requestId)) {
            throw new AccountException(AccountErrorCode.ADMIN_REQUEST_CONFLICT,
                    "request ID conflicts with an existing wallet operation");
        }
        mapper.lockWallet(accountId);
        Instant now = clock.instant();
        if (mapper.adjustWallet(accountId, chipDelta, crystalDelta, now) != 1) {
            throw new AccountException(AccountErrorCode.INVALID_ADMIN_ADJUSTMENT,
                    "wallet balance cannot become negative");
        }
        WalletSnapshot result = wallet(accountId);
        if (chipDelta != 0) mapper.insertLedger(UUID.randomUUID(), accountId, "CHIP", chipDelta,
                result.chips(), "ADMIN_ADJUSTMENT", requestId, now);
        if (crystalDelta != 0) mapper.insertLedger(UUID.randomUUID(), accountId, "CRYSTAL", crystalDelta,
                result.spiritCrystals(), "ADMIN_ADJUSTMENT", requestId, now);
        mapper.insertAdminOperation(requestId, principal.accountId(), "ADJUST_WALLET", targetKey, now);
        mapper.insertAdminAudit(UUID.randomUUID(), principal.accountId(), "ADJUST_WALLET", accountId.toString(),
                "chips=" + chipDelta + ", crystals=" + crystalDelta + ", reason=" + note, now);
        return new AdminWalletAdjustment(accountId, result);
    }

    @Override
    @Transactional(transactionManager = "historyTransactionManager")
    public void resetAccountPassword(String sessionToken, String requestId, UUID accountId, String newPassword) {
        AccountPrincipal principal = authenticate(sessionToken);
        requireAdmin(principal);
        validateRequestId(requestId);
        validatePassword(newPassword);
        if (accountId == null || !mapper.accountExists(accountId)) {
            throw new AccountException(AccountErrorCode.ACCOUNT_NOT_FOUND, "account was not found");
        }
        String targetKey = accountId.toString();
        AdminOperationRow repeated = mapper.findAdminOperation(requestId);
        if (repeated != null) {
            requireSameAdminOperation(repeated, "RESET_PASSWORD", targetKey);
            return;
        }
        Instant now = clock.instant();
        mapper.updatePassword(accountId, passwords.encode(newPassword), now);
        mapper.revokeAllSessions(accountId, now);
        mapper.insertAdminOperation(requestId, principal.accountId(), "RESET_PASSWORD", targetKey, now);
        mapper.insertAdminAudit(UUID.randomUUID(), principal.accountId(), "RESET_PASSWORD", targetKey,
                "all existing sessions revoked; password value was not recorded", now);
    }

    @Override
    @Transactional(transactionManager = "historyTransactionManager")
    public AdminFriendshipView createAdminFriendship(
            String sessionToken, String requestId, UUID firstAccountId, UUID secondAccountId
    ) {
        AccountPrincipal principal = authenticate(sessionToken);
        requireAdmin(principal);
        validateRequestId(requestId);
        validateAdminFriendPair(firstAccountId, secondAccountId);
        String targetKey = orderedPair(firstAccountId, secondAccountId);
        AdminOperationRow repeated = mapper.findAdminOperation(requestId);
        if (repeated != null) {
            requireSameAdminOperation(repeated, "CREATE_FRIENDSHIP", targetKey);
            return requireAdminFriendship(firstAccountId, secondAccountId);
        }
        Instant now = clock.instant();
        FriendshipStateRow existing = mapper.lockFriendshipPair(firstAccountId, secondAccountId);
        if (existing == null) {
            mapper.insertFriendship(UUID.randomUUID(), firstAccountId, secondAccountId, "ACCEPTED", now);
        } else if ("PENDING".equals(existing.status())) {
            mapper.acceptFriendship(existing.friendshipId(), now);
        }
        mapper.insertAdminOperation(requestId, principal.accountId(), "CREATE_FRIENDSHIP", targetKey, now);
        mapper.insertAdminAudit(UUID.randomUUID(), principal.accountId(), "CREATE_FRIENDSHIP", targetKey,
                "administrator established accepted friendship", now);
        return requireAdminFriendship(firstAccountId, secondAccountId);
    }

    @Override
    @Transactional(transactionManager = "historyTransactionManager")
    public void removeAdminFriendship(String sessionToken, String requestId, UUID friendshipId) {
        AccountPrincipal principal = authenticate(sessionToken);
        requireAdmin(principal);
        validateRequestId(requestId);
        if (friendshipId == null) throw new AccountException(AccountErrorCode.FRIENDSHIP_NOT_FOUND,
                "friendship was not found");
        String targetKey = friendshipId.toString();
        AdminOperationRow repeated = mapper.findAdminOperation(requestId);
        if (repeated != null) {
            requireSameAdminOperation(repeated, "REMOVE_FRIENDSHIP", targetKey);
            return;
        }
        if (mapper.lockFriendship(friendshipId) == null) {
            throw new AccountException(AccountErrorCode.FRIENDSHIP_NOT_FOUND, "friendship was not found");
        }
        mapper.deleteFriendship(friendshipId);
        Instant now = clock.instant();
        mapper.insertAdminOperation(requestId, principal.accountId(), "REMOVE_FRIENDSHIP", targetKey, now);
        mapper.insertAdminAudit(UUID.randomUUID(), principal.accountId(), "REMOVE_FRIENDSHIP", targetKey,
                "administrator removed friendship", now);
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
                account == null ? AvatarCatalog.DEFAULT : account.avatarKey(),
                account != null && account.admin(), mapper.listCosmetics(accountId), loadout(accountId));
    }

    private StorePurchaseResult purchaseResult(UUID accountId, StoreItem item) {
        return new StorePurchaseResult(item, wallet(accountId), mapper.listCosmetics(accountId), loadout(accountId));
    }

    private CosmeticLoadout loadout(UUID accountId) {
        String avatarFrame = null;
        String cardBack = null;
        String title = null;
        String buttonEffect = null;
        String victoryEffect = null;
        String profileStyle = null;
        for (CosmeticLoadoutRow row : mapper.listCosmeticLoadout(accountId)) {
            switch (CosmeticSlot.fromApi(row.slot())) {
                case AVATAR_FRAME -> avatarFrame = row.skinKey();
                case CARD_BACK -> cardBack = row.skinKey();
                case TITLE -> title = row.skinKey();
                case BUTTON_EFFECT -> buttonEffect = row.skinKey();
                case VICTORY_EFFECT -> victoryEffect = row.skinKey();
                case PROFILE_STYLE -> profileStyle = row.skinKey();
            }
        }
        return new CosmeticLoadout(avatarFrame, cardBack, title, buttonEffect, victoryEffect, profileStyle);
    }

    private WalletSnapshot wallet(UUID accountId) {
        return new WalletSnapshot(mapper.chipBalance(accountId), mapper.crystalBalance(accountId));
    }

    private FriendDashboard mutateFriendship(
            String sessionToken, String requestId, UUID friendshipId, String operation
    ) {
        AccountPrincipal principal = authenticate(sessionToken);
        validateRequestId(requestId);
        if (friendshipId == null) throw new AccountException(AccountErrorCode.FRIENDSHIP_NOT_FOUND,
                "friendship was not found");
        String targetKey = friendshipId.toString();
        FriendOperationRow repeated = mapper.findFriendOperation(principal.accountId(), requestId);
        if (repeated != null) {
            requireSameFriendOperation(repeated, operation, targetKey);
            return friendDashboard(principal.accountId());
        }
        FriendshipStateRow row = mapper.lockFriendship(friendshipId);
        if (row == null) throw new AccountException(AccountErrorCode.FRIENDSHIP_NOT_FOUND,
                "friendship was not found");
        boolean participant = row.requesterId().equals(principal.accountId())
                || row.addresseeId().equals(principal.accountId());
        if (!participant) throw new AccountException(AccountErrorCode.FORBIDDEN,
                "friendship does not belong to this account");
        switch (operation) {
            case "ACCEPT" -> {
                if (!row.addresseeId().equals(principal.accountId()) || !"PENDING".equals(row.status())) {
                    throw new AccountException(AccountErrorCode.INVALID_FRIENDSHIP,
                            "only the recipient may accept a pending request");
                }
                mapper.acceptFriendship(friendshipId, clock.instant());
            }
            case "REJECT" -> {
                if (!row.addresseeId().equals(principal.accountId()) || !"PENDING".equals(row.status())) {
                    throw new AccountException(AccountErrorCode.INVALID_FRIENDSHIP,
                            "only the recipient may reject a pending request");
                }
                mapper.deleteFriendship(friendshipId);
            }
            case "REMOVE" -> {
                if (!"ACCEPTED".equals(row.status())) {
                    throw new AccountException(AccountErrorCode.INVALID_FRIENDSHIP,
                            "only accepted friendships may be removed");
                }
                mapper.deleteFriendship(friendshipId);
            }
            default -> throw new IllegalArgumentException("friend operation is invalid");
        }
        mapper.insertFriendOperation(principal.accountId(), requestId, operation, targetKey, clock.instant());
        return friendDashboard(principal.accountId());
    }

    private FriendDashboard friendDashboard(UUID accountId) {
        Instant now = clock.instant();
        List<FriendView> friends = new java.util.ArrayList<>();
        List<FriendView> incoming = new java.util.ArrayList<>();
        List<FriendView> outgoing = new java.util.ArrayList<>();
        for (FriendshipViewRow row : mapper.listFriendships(accountId, now, now.minus(PRESENCE_TTL))) {
            boolean requester = row.requesterId().equals(accountId);
            FriendView other = requester
                    ? new FriendView(row.friendshipId(), row.addresseeGameId(), row.addresseeAvatarKey(), row.addresseeOnline())
                    : new FriendView(row.friendshipId(), row.requesterGameId(), row.requesterAvatarKey(), row.requesterOnline());
            if ("ACCEPTED".equals(row.status())) friends.add(other);
            else if (requester) outgoing.add(other);
            else incoming.add(other);
        }
        return new FriendDashboard(friends, incoming, outgoing);
    }

    private void validateAdminFriendPair(UUID first, UUID second) {
        if (first == null || second == null || first.equals(second)) {
            throw new AccountException(AccountErrorCode.INVALID_FRIENDSHIP, "two different accounts are required");
        }
        if (!mapper.accountExists(first) || !mapper.accountExists(second)) {
            throw new AccountException(AccountErrorCode.ACCOUNT_NOT_FOUND, "account was not found");
        }
    }

    private AdminFriendshipView requireAdminFriendship(UUID first, UUID second) {
        FriendshipStateRow state = mapper.lockFriendshipPair(first, second);
        if (state == null) throw new IllegalStateException("administrator friendship state is missing");
        return mapper.listAllFriendships().stream()
                .filter(row -> row.friendshipId().equals(state.friendshipId()))
                .map(PostgresAccountService::adminFriendship)
                .findFirst().orElseThrow(() -> new IllegalStateException("administrator friendship view is missing"));
    }

    private static AdminFriendshipView adminFriendship(FriendshipViewRow row) {
        return new AdminFriendshipView(row.friendshipId(), row.requesterId(), row.requesterGameId(),
                row.addresseeId(), row.addresseeGameId(), row.status(), row.updatedAt());
    }

    private static String orderedPair(UUID first, UUID second) {
        String a = first.toString();
        String b = second.toString();
        return a.compareTo(b) <= 0 ? a + ":" + b : b + ":" + a;
    }

    private static void requireSameFriendOperation(FriendOperationRow row, String operation, String target) {
        if (!operation.equals(row.operation()) || !target.equals(row.targetKey())) {
            throw new AccountException(AccountErrorCode.FRIEND_REQUEST_CONFLICT,
                    "request ID was already used for another friend operation");
        }
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

    private static MailItem mailItem(MailRow row) {
        if (row == null) throw new AccountException(AccountErrorCode.MAIL_NOT_FOUND, "mail was not found");
        return new MailItem(row.mailId(), row.type(), row.subject(), row.body(), row.rewardChips(),
                row.rewardCrystals(), row.rewardSkinKey(), row.createdAt(), row.readAt() != null,
                row.claimedAt() != null);
    }

    private static BroadcastMailCommand validateMail(BroadcastMailCommand command) {
        if (command == null) throw new AccountException(AccountErrorCode.INVALID_MAIL, "mail is required");
        String type = command.type() == null ? "" : command.type().trim().toUpperCase(Locale.ROOT);
        if (!List.of("ANNOUNCEMENT", "NOTICE", "REWARD").contains(type)) {
            throw new AccountException(AccountErrorCode.INVALID_MAIL, "mail type is invalid");
        }
        String subject = command.subject() == null ? "" : command.subject().trim();
        String body = command.body() == null ? "" : command.body().trim();
        if (subject.isEmpty() || subject.length() > 80 || body.isEmpty() || body.length() > 2000
                || command.rewardChips() < 0 || command.rewardCrystals() < 0
                || command.rewardChips() > 10_000_000 || command.rewardCrystals() > 1_000_000) {
            throw new AccountException(AccountErrorCode.INVALID_MAIL, "mail content or reward is invalid");
        }
        String skin = command.rewardSkinKey() == null ? null : command.rewardSkinKey().trim().toLowerCase(Locale.ROOT);
        if (skin != null && skin.isEmpty()) skin = null;
        if (skin != null && !SKIN_KEY.matcher(skin).matches()) {
            throw new AccountException(AccountErrorCode.INVALID_MAIL, "skin key is invalid");
        }
        if (skin != null) {
            try {
                StoreItem skinItem = CosmeticCatalog.require(skin);
                CosmeticCatalog.slot(skinItem);
                skin = skinItem.key();
            } catch (AccountException invalidSkin) {
                throw new AccountException(AccountErrorCode.INVALID_MAIL,
                        "reward skin is not an equipable catalog item");
            }
        }
        if (!"REWARD".equals(type) && (command.rewardChips() > 0 || command.rewardCrystals() > 0 || skin != null)) {
            throw new AccountException(AccountErrorCode.INVALID_MAIL, "only reward mail may contain attachments");
        }
        return new BroadcastMailCommand(type, subject, body, command.rewardChips(), command.rewardCrystals(), skin);
    }

    private void requireAdmin(AccountPrincipal principal) {
        AccountRow sender = mapper.findByGameId(principal.gameId());
        if (sender == null || !sender.accountId().equals(principal.accountId()) || !sender.admin()) {
            throw new AccountException(AccountErrorCode.FORBIDDEN, "administrator access is required");
        }
    }

    private static AdminRedemptionCode adminCode(RedemptionCodeRow row) {
        if (row == null) throw new IllegalStateException("redemption code state is missing");
        return new AdminRedemptionCode(row.codeHash(), row.currency(), row.rewardAmount(),
                row.maxRedemptions(), row.redeemedCount(), row.validFrom(), row.validUntil(),
                row.enabled(), row.createdAt());
    }

    private static void requireSameAdminOperation(AdminOperationRow row, String operation, String target) {
        if (!operation.equals(row.operation()) || !target.equals(row.targetKey())) {
            throw new AccountException(AccountErrorCode.ADMIN_REQUEST_CONFLICT,
                    "request ID was already used for another administrator operation");
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
