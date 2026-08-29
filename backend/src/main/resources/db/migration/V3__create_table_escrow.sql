CREATE TABLE table_buy_in (
    escrow_id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES identity_account(account_id),
    game_id VARCHAR(64) NOT NULL REFERENCES poker_user(player_id),
    room_id VARCHAR(64) NOT NULL,
    buy_in BIGINT NOT NULL CHECK (buy_in > 0),
    returned_chips BIGINT CHECK (returned_chips >= 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'SETTLED')),
    reserve_request_id VARCHAR(64) NOT NULL,
    settle_request_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    settled_at TIMESTAMPTZ,
    UNIQUE (reserve_request_id),
    UNIQUE (settle_request_id)
);

-- A real identity may own aliases, but may occupy only one funded seat at a time.
CREATE UNIQUE INDEX uq_table_buy_in_one_active_account
    ON table_buy_in(account_id) WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX uq_table_buy_in_one_active_seat
    ON table_buy_in(room_id, game_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_table_buy_in_active_room ON table_buy_in(room_id) WHERE status = 'ACTIVE';
