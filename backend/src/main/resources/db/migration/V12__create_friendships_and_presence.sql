ALTER TABLE account_session
    ADD COLUMN last_seen_at TIMESTAMPTZ;

CREATE INDEX idx_account_session_presence
    ON account_session (account_id, last_seen_at DESC)
    WHERE revoked_at IS NULL;

CREATE TABLE friendship (
    friendship_id   UUID PRIMARY KEY,
    requester_id    UUID NOT NULL REFERENCES identity_account(account_id) ON DELETE CASCADE,
    addressee_id    UUID NOT NULL REFERENCES identity_account(account_id) ON DELETE CASCADE,
    status          VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED')),
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    CHECK (requester_id <> addressee_id)
);

CREATE UNIQUE INDEX uq_friendship_account_pair
    ON friendship (LEAST(requester_id, addressee_id), GREATEST(requester_id, addressee_id));

CREATE INDEX idx_friendship_requester ON friendship (requester_id, status);
CREATE INDEX idx_friendship_addressee ON friendship (addressee_id, status);

CREATE TABLE friend_operation_request (
    account_id      UUID NOT NULL REFERENCES identity_account(account_id) ON DELETE CASCADE,
    request_id      VARCHAR(64) NOT NULL,
    operation       VARCHAR(24) NOT NULL,
    target_key      VARCHAR(96) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (account_id, request_id)
);

CREATE TABLE admin_audit_log (
    audit_id           UUID PRIMARY KEY,
    admin_account_id   UUID NOT NULL REFERENCES identity_account(account_id),
    action             VARCHAR(32) NOT NULL,
    target_key         VARCHAR(96) NOT NULL,
    details            VARCHAR(240) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_admin_audit_created
    ON admin_audit_log (created_at DESC);
