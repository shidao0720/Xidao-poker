CREATE TABLE admin_operation_request (
    request_id         VARCHAR(64) PRIMARY KEY,
    admin_account_id   UUID NOT NULL REFERENCES identity_account(account_id),
    operation          VARCHAR(32) NOT NULL,
    target_key         VARCHAR(96) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_admin_operation_account
    ON admin_operation_request (admin_account_id, created_at DESC);
