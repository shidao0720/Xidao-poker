CREATE TABLE cosmetic_purchase (
    purchase_id       UUID PRIMARY KEY,
    account_id        UUID NOT NULL REFERENCES identity_account(account_id) ON DELETE CASCADE,
    catalog_key       VARCHAR(64) NOT NULL,
    crystal_cost      BIGINT NOT NULL CHECK (crystal_cost >= 0),
    request_id        VARCHAR(64) NOT NULL,
    purchased_at      TIMESTAMPTZ NOT NULL,
    UNIQUE (account_id, request_id)
);

ALTER TABLE account_cosmetic
    ADD COLUMN source_purchase_id UUID REFERENCES cosmetic_purchase(purchase_id);

CREATE TABLE account_cosmetic_loadout (
    account_id        UUID NOT NULL REFERENCES identity_account(account_id) ON DELETE CASCADE,
    slot              VARCHAR(24) NOT NULL CHECK (slot IN (
        'AVATAR_FRAME', 'CARD_BACK', 'TITLE', 'BUTTON_EFFECT', 'VICTORY_EFFECT', 'PROFILE_STYLE'
    )),
    skin_key          VARCHAR(64) NOT NULL,
    equipped_at       TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (account_id, slot),
    FOREIGN KEY (account_id, skin_key)
        REFERENCES account_cosmetic(account_id, skin_key) ON DELETE CASCADE
);

CREATE INDEX idx_cosmetic_purchase_account ON cosmetic_purchase (account_id, purchased_at DESC);
