CREATE TABLE redemption_code (
    code_hash        CHAR(64) PRIMARY KEY,
    currency         VARCHAR(16) NOT NULL CHECK (currency IN ('CHIP', 'CRYSTAL')),
    reward_amount    BIGINT NOT NULL CHECK (reward_amount > 0),
    max_redemptions  INTEGER CHECK (max_redemptions IS NULL OR max_redemptions > 0),
    redeemed_count   INTEGER NOT NULL DEFAULT 0 CHECK (redeemed_count >= 0),
    valid_from       TIMESTAMPTZ NOT NULL,
    valid_until      TIMESTAMPTZ,
    enabled          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL,
    CHECK (valid_until IS NULL OR valid_until > valid_from),
    CHECK (max_redemptions IS NULL OR redeemed_count <= max_redemptions)
);

CREATE TABLE redemption_claim (
    code_hash        CHAR(64) NOT NULL REFERENCES redemption_code(code_hash),
    account_id       UUID NOT NULL REFERENCES identity_account(account_id) ON DELETE CASCADE,
    request_id       VARCHAR(64) NOT NULL,
    currency         VARCHAR(16) NOT NULL CHECK (currency IN ('CHIP', 'CRYSTAL')),
    reward_amount    BIGINT NOT NULL CHECK (reward_amount > 0),
    claimed_at       TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (code_hash, account_id),
    UNIQUE (account_id, request_id)
);

CREATE INDEX idx_redemption_claim_account
    ON redemption_claim (account_id, claimed_at DESC);

-- 首个公开测试码：FATE-STAY-POKER；每个账户限领一次 100 英魂结晶。
INSERT INTO redemption_code
    (code_hash, currency, reward_amount, max_redemptions, redeemed_count,
     valid_from, valid_until, enabled, created_at)
VALUES
    ('0488d3dfa6c296ebaff116be233dd5ecfaa846ee36a6aed4d4965a007a80a2d9',
     'CRYSTAL', 100, NULL, 0, TIMESTAMPTZ '2026-01-01 00:00:00+00', NULL, TRUE, NOW());
