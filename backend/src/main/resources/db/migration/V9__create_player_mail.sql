ALTER TABLE identity_account
    ADD COLUMN is_admin BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE identity_account
SET is_admin = TRUE
WHERE account_id = (
    SELECT account_id FROM identity_account ORDER BY created_at ASC, account_id ASC LIMIT 1
);

CREATE TABLE mail_message (
    mail_id             UUID PRIMARY KEY,
    request_id          VARCHAR(64) NOT NULL UNIQUE,
    sender_account_id   UUID NOT NULL REFERENCES identity_account(account_id),
    type                 VARCHAR(16) NOT NULL CHECK (type IN ('ANNOUNCEMENT', 'NOTICE', 'REWARD')),
    subject              VARCHAR(80) NOT NULL,
    body                 VARCHAR(2000) NOT NULL,
    reward_chips         BIGINT NOT NULL DEFAULT 0 CHECK (reward_chips >= 0),
    reward_crystals      BIGINT NOT NULL DEFAULT 0 CHECK (reward_crystals >= 0),
    reward_skin_key      VARCHAR(64),
    created_at           TIMESTAMPTZ NOT NULL
);

CREATE TABLE account_mail (
    mail_id              UUID NOT NULL REFERENCES mail_message(mail_id) ON DELETE CASCADE,
    account_id           UUID NOT NULL REFERENCES identity_account(account_id) ON DELETE CASCADE,
    read_at              TIMESTAMPTZ,
    claimed_at           TIMESTAMPTZ,
    claim_request_id     VARCHAR(64),
    PRIMARY KEY (mail_id, account_id),
    UNIQUE (account_id, claim_request_id)
);

CREATE INDEX idx_account_mail_inbox ON account_mail (account_id, read_at);

CREATE TABLE account_cosmetic (
    account_id           UUID NOT NULL REFERENCES identity_account(account_id) ON DELETE CASCADE,
    skin_key             VARCHAR(64) NOT NULL,
    acquired_at          TIMESTAMPTZ NOT NULL,
    source_mail_id       UUID REFERENCES mail_message(mail_id),
    PRIMARY KEY (account_id, skin_key)
);
