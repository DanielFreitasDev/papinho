-- Notificações (NT-01, NT-03): preferências por pessoa, assinaturas de Web Push por navegador e o par de chaves
-- VAPID da instância (a privada cifrada com o SecretCipher, como as outras chaves guardadas no banco).
CREATE TABLE notification_preferences (
    user_id    UUID                     NOT NULL,
    enabled    BOOLEAN                  NOT NULL DEFAULT TRUE,
    direct     BOOLEAN                  NOT NULL DEFAULT TRUE,
    groups     BOOLEAN                  NOT NULL DEFAULT TRUE,
    mentions   BOOLEAN                  NOT NULL DEFAULT TRUE,
    sound      BOOLEAN                  NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_notification_preferences PRIMARY KEY (user_id),
    CONSTRAINT fk_notification_preferences_user FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE
);

CREATE TABLE push_subscriptions (
    id           UUID                     NOT NULL,
    user_id      UUID                     NOT NULL,
    -- URL do serviço de push do navegador; só hosts da lista permitida (sem SSRF).
    endpoint     TEXT                     NOT NULL,
    p256dh       VARCHAR(100)             NOT NULL,
    auth         VARCHAR(40)              NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    last_used_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_push_subscriptions PRIMARY KEY (id),
    CONSTRAINT uq_push_subscriptions_endpoint UNIQUE (endpoint),
    CONSTRAINT fk_push_subscriptions_user FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT ck_push_subscriptions_endpoint CHECK (char_length(endpoint) <= 1024 AND endpoint LIKE 'https://%')
);
CREATE INDEX ix_push_subscriptions_user_id ON push_subscriptions (user_id);

CREATE TABLE push_vapid_keys (
    id          SMALLINT                 NOT NULL,
    public_key  VARCHAR(100)             NOT NULL,
    private_key TEXT                     NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_push_vapid_keys PRIMARY KEY (id),
    CONSTRAINT ck_push_vapid_keys_single CHECK (id = 1)
);
