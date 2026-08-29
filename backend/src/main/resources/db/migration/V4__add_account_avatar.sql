ALTER TABLE identity_account
    ADD COLUMN avatar_key VARCHAR(32) NOT NULL DEFAULT 'default';

ALTER TABLE identity_account
    ADD CONSTRAINT ck_identity_account_avatar_key
    CHECK (avatar_key IN ('default', 'azure', 'crimson', 'violet', 'moon', 'prism'));
