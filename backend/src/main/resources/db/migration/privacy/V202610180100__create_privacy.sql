-- Privacidade (Fase 5). Sem linha em privacy_settings valem os padrões do WhatsApp: tudo visível, confirmação de
-- leitura ligada, notificação com conteúdo e nenhum desfoque.
CREATE TABLE privacy_settings (
    user_id                   UUID                     NOT NULL,
    -- PV-04 e PV-07: EVERYONE, CONTACTS, CONTACTS_EXCEPT ou NOBODY.
    last_seen                 VARCHAR(16)              NOT NULL DEFAULT 'EVERYONE',
    online                    VARCHAR(16)              NOT NULL DEFAULT 'EVERYONE',
    photo                     VARCHAR(16)              NOT NULL DEFAULT 'EVERYONE',
    about                     VARCHAR(16)              NOT NULL DEFAULT 'EVERYONE',
    groups_add                VARCHAR(16)              NOT NULL DEFAULT 'EVERYONE',
    -- PV-06: padrão global das conversas individuais; grupos e conversas podem sobrepor (conversation_members).
    read_receipts             BOOLEAN                  NOT NULL DEFAULT TRUE,
    -- PV-08: notificações mostram só "Nova mensagem".
    hide_notification_content BOOLEAN                  NOT NULL DEFAULT FALSE,
    -- PV-01: modo privado e o que ele desfoca; a revelação pode esperar reveal_delay_ms ao passar o mouse.
    private_mode              BOOLEAN                  NOT NULL DEFAULT FALSE,
    blur_messages             BOOLEAN                  NOT NULL DEFAULT TRUE,
    blur_previews             BOOLEAN                  NOT NULL DEFAULT TRUE,
    blur_names                BOOLEAN                  NOT NULL DEFAULT FALSE,
    blur_photos               BOOLEAN                  NOT NULL DEFAULT FALSE,
    blur_media                BOOLEAN                  NOT NULL DEFAULT TRUE,
    reveal_delay_ms           INTEGER                  NOT NULL DEFAULT 0,
    updated_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_privacy_settings PRIMARY KEY (user_id),
    CONSTRAINT fk_privacy_settings_user FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT ck_privacy_settings_values CHECK (
        last_seen IN ('EVERYONE', 'CONTACTS', 'CONTACTS_EXCEPT', 'NOBODY')
        AND online IN ('EVERYONE', 'CONTACTS', 'CONTACTS_EXCEPT', 'NOBODY')
        AND photo IN ('EVERYONE', 'CONTACTS', 'CONTACTS_EXCEPT', 'NOBODY')
        AND about IN ('EVERYONE', 'CONTACTS', 'CONTACTS_EXCEPT', 'NOBODY')
        AND groups_add IN ('EVERYONE', 'CONTACTS', 'CONTACTS_EXCEPT', 'NOBODY')),
    CONSTRAINT ck_privacy_settings_delay CHECK (reveal_delay_ms BETWEEN 0 AND 10000)
);

-- "Meus contatos exceto…": quem fica de fora de cada item.
CREATE TABLE privacy_exceptions (
    user_id     UUID        NOT NULL,
    setting     VARCHAR(16) NOT NULL,
    excluded_id UUID        NOT NULL,
    CONSTRAINT pk_privacy_exceptions PRIMARY KEY (user_id, setting, excluded_id),
    CONSTRAINT fk_privacy_exceptions_user FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT fk_privacy_exceptions_excluded FOREIGN KEY (excluded_id) REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT ck_privacy_exceptions_setting CHECK (setting IN ('LAST_SEEN', 'ONLINE', 'PHOTO', 'ABOUT', 'GROUPS_ADD'))
);

-- PV-02: segredo próprio do bloqueio de tela (PIN ou senha, Argon2) e os gatilhos, todos desligados por padrão.
CREATE TABLE screen_locks (
    user_id        UUID                     NOT NULL,
    secret_kind    VARCHAR(8)               NOT NULL,
    secret_hash    VARCHAR(255)             NOT NULL,
    idle_minutes   INTEGER,
    lock_on_hidden BOOLEAN                  NOT NULL DEFAULT FALSE,
    shortcut       VARCHAR(48),
    allow_passkey  BOOLEAN                  NOT NULL DEFAULT FALSE,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_screen_locks PRIMARY KEY (user_id),
    CONSTRAINT fk_screen_locks_user FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT ck_screen_locks_kind CHECK (secret_kind IN ('PIN', 'PASSWORD')),
    CONSTRAINT ck_screen_locks_idle CHECK (idle_minutes IS NULL OR idle_minutes BETWEEN 1 AND 1440)
);

-- PV-03: códigos de conversas trancadas (um por conversa ou um compartilhado por várias), com hash Argon2.
CREATE TABLE lock_codes (
    id         UUID                     NOT NULL,
    user_id    UUID                     NOT NULL,
    code_hash  VARCHAR(255)             NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_lock_codes PRIMARY KEY (id),
    CONSTRAINT fk_lock_codes_user FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE
);
CREATE INDEX ix_lock_codes_user ON lock_codes (user_id);

-- Conversa trancada por quem a trancou; o id da conversa é opaco aqui (a participação é checada pelo módulo
-- de mensagens) e a linha some com a conversa.
CREATE TABLE locked_conversations (
    user_id         UUID                     NOT NULL,
    conversation_id UUID                     NOT NULL,
    code_id         UUID                     NOT NULL,
    locked_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_locked_conversations PRIMARY KEY (user_id, conversation_id),
    CONSTRAINT fk_locked_conversations_user FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT fk_locked_conversations_conversation FOREIGN KEY (conversation_id)
        REFERENCES conversations (id) ON DELETE CASCADE,
    CONSTRAINT fk_locked_conversations_code FOREIGN KEY (code_id) REFERENCES lock_codes (id) ON DELETE CASCADE
);
CREATE INDEX ix_locked_conversations_code ON locked_conversations (code_id);
