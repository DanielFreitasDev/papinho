-- E-mail opcional de recuperação (ID-05): só vale depois de confirmado pelo link enviado ao endereço.
ALTER TABLE user_accounts
    ADD COLUMN email             VARCHAR(254),
    ADD COLUMN email_verified_at TIMESTAMP WITH TIME ZONE;

-- Links de uso único enviados por e-mail: só o hash SHA-256 do token fica no banco.
CREATE TABLE email_tokens (
    token_hash VARCHAR(64)              NOT NULL,
    user_id    UUID                     NOT NULL,
    purpose    TEXT                     NOT NULL,
    email      VARCHAR(254)             NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at    TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_email_tokens PRIMARY KEY (token_hash),
    CONSTRAINT fk_email_tokens_user FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT ck_email_tokens_purpose CHECK (purpose IN ('VERIFY', 'RESET'))
);
CREATE INDEX ix_email_tokens_user_id ON email_tokens (user_id, purpose);
