ALTER TABLE identity_account
    DROP CONSTRAINT ck_identity_account_avatar_key;

ALTER TABLE identity_account
    ADD CONSTRAINT ck_identity_account_avatar_key
    CHECK (avatar_key IN (
        'default', 'azure', 'crimson', 'violet', 'moon', 'prism',
        'bobo', 'god', 'lian', 'niu', 'yun', 'zhen', 'zhi'
    ));
