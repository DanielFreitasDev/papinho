-- Contas, reservas de @usuario, códigos de recuperação e convites (ID-01 a ID-08).
CREATE TABLE user_accounts (
    id                   UUID                     NOT NULL,
    username             VARCHAR(32)              NOT NULL,
    password_hash        TEXT                     NOT NULL,
    role                 TEXT                     NOT NULL,
    enabled              BOOLEAN                  NOT NULL DEFAULT TRUE,
    totp_secret          TEXT,
    totp_enabled         BOOLEAN                  NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    username_changed_at  TIMESTAMP WITH TIME ZONE,
    version              BIGINT                   NOT NULL DEFAULT 0,
    CONSTRAINT pk_user_accounts PRIMARY KEY (id),
    -- O valor já chega normalizado (NFKC + minúsculas); a constraint garante a unicidade case-insensitive.
    CONSTRAINT uq_user_accounts_username UNIQUE (username),
    CONSTRAINT ck_user_accounts_username_format CHECK (
        username ~ '^[a-z0-9._]{3,32}$' AND username NOT LIKE '.%' AND username NOT LIKE '%.' AND username NOT LIKE '%..%'),
    CONSTRAINT ck_user_accounts_role CHECK (role IN ('USER', 'ADMIN'))
);

CREATE TABLE username_reservations (
    username       VARCHAR(32)              NOT NULL,
    user_id        UUID                     NOT NULL,
    reserved_until TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_username_reservations PRIMARY KEY (username),
    CONSTRAINT fk_username_reservations_user FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE
);

CREATE TABLE recovery_codes (
    id        UUID                     NOT NULL,
    user_id   UUID                     NOT NULL,
    code_hash TEXT                     NOT NULL,
    used_at   TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_recovery_codes PRIMARY KEY (id),
    CONSTRAINT fk_recovery_codes_user FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE
);
CREATE INDEX ix_recovery_codes_user_id ON recovery_codes (user_id);

CREATE TABLE invites (
    id         UUID                     NOT NULL,
    token_hash VARCHAR(64)              NOT NULL,
    created_by UUID                     NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    max_uses   INT                      NOT NULL,
    uses       INT                      NOT NULL DEFAULT 0,
    revoked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_invites PRIMARY KEY (id),
    CONSTRAINT uq_invites_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_invites_created_by FOREIGN KEY (created_by) REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT ck_invites_uses CHECK (uses >= 0 AND uses <= max_uses AND max_uses BETWEEN 1 AND 100)
);
CREATE INDEX ix_invites_created_by ON invites (created_by, created_at DESC);
