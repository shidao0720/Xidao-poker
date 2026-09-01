ALTER TABLE identity_account
    ADD COLUMN email VARCHAR(254),
    ADD COLUMN email_key VARCHAR(254);

CREATE UNIQUE INDEX uq_identity_account_email_key
    ON identity_account (email_key)
    WHERE email_key IS NOT NULL;
