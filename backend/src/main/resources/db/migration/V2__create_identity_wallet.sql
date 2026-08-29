CREATE TABLE identity_account (
    account_id        UUID PRIMARY KEY,
    real_name         VARCHAR(64) NOT NULL,
    real_name_key     VARCHAR(128) NOT NULL UNIQUE,
    password_hash     VARCHAR(100) NOT NULL,
    primary_game_id   VARCHAR(64),
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL
);

ALTER TABLE poker_user
    ADD COLUMN account_id UUID REFERENCES identity_account(account_id);

CREATE INDEX idx_poker_user_account ON poker_user (account_id);

ALTER TABLE identity_account
    ADD CONSTRAINT fk_identity_primary_game
    FOREIGN KEY (primary_game_id) REFERENCES poker_user(player_id);

CREATE TABLE account_wallet (
    account_id        UUID PRIMARY KEY REFERENCES identity_account(account_id) ON DELETE CASCADE,
    chip_balance      BIGINT NOT NULL DEFAULT 10000 CHECK (chip_balance >= 0),
    crystal_balance   BIGINT NOT NULL DEFAULT 0 CHECK (crystal_balance >= 0),
    version           BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    updated_at        TIMESTAMPTZ NOT NULL
);

CREATE TABLE account_session (
    token_hash        CHAR(64) PRIMARY KEY,
    account_id        UUID NOT NULL REFERENCES identity_account(account_id) ON DELETE CASCADE,
    game_id           VARCHAR(64) NOT NULL REFERENCES poker_user(player_id),
    created_at        TIMESTAMPTZ NOT NULL,
    expires_at        TIMESTAMPTZ NOT NULL,
    revoked_at        TIMESTAMPTZ,
    CHECK (expires_at > created_at)
);

CREATE INDEX idx_account_session_account ON account_session (account_id, expires_at DESC);

CREATE TABLE daily_check_in (
    account_id        UUID NOT NULL REFERENCES identity_account(account_id) ON DELETE CASCADE,
    check_in_date     DATE NOT NULL,
    request_id        VARCHAR(64) NOT NULL,
    awarded_chips     BIGINT NOT NULL CHECK (awarded_chips > 0),
    created_at        TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (account_id, check_in_date),
    UNIQUE (account_id, request_id)
);

CREATE TABLE wallet_ledger (
    ledger_id         UUID PRIMARY KEY,
    account_id        UUID NOT NULL REFERENCES identity_account(account_id) ON DELETE CASCADE,
    currency          VARCHAR(16) NOT NULL CHECK (currency IN ('CHIP', 'CRYSTAL')),
    delta             BIGINT NOT NULL CHECK (delta <> 0),
    balance_after     BIGINT NOT NULL CHECK (balance_after >= 0),
    reason            VARCHAR(32) NOT NULL,
    request_id        VARCHAR(64) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL,
    UNIQUE (account_id, currency, request_id)
);

CREATE INDEX idx_wallet_ledger_account ON wallet_ledger (account_id, created_at DESC);
