-- Mensagens temporárias (MS-07): o timer é da conversa (vale para todos os participantes) e cada mensagem
-- nasce com a hora de expirar; um job com ShedLock apaga as vencidas e os arquivos delas.
ALTER TABLE conversations ADD COLUMN disappearing_seconds INT;
ALTER TABLE conversations ADD CONSTRAINT ck_conversations_disappearing
    CHECK (disappearing_seconds IS NULL OR disappearing_seconds IN (86400, 604800, 7776000));

ALTER TABLE messages ADD COLUMN expires_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX ix_messages_expires_at ON messages (expires_at) WHERE expires_at IS NOT NULL;

-- Preferências de mensagens de cada pessoa: timer padrão para conversas novas.
CREATE TABLE messaging_preferences (
    user_id                      UUID                     NOT NULL,
    default_disappearing_seconds INT,
    updated_at                   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_messaging_preferences PRIMARY KEY (user_id),
    CONSTRAINT fk_messaging_preferences_user FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT ck_messaging_preferences_disappearing
        CHECK (default_disappearing_seconds IS NULL OR default_disappearing_seconds IN (86400, 604800, 7776000))
);
