-- Mensagens agendadas, envio silencioso e rascunho sincronizado entre dispositivos (MS-11).
ALTER TABLE messages ADD COLUMN silent BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE scheduled_messages (
    id                UUID                     NOT NULL,
    conversation_id   UUID                     NOT NULL,
    sender_id         UUID                     NOT NULL,
    client_message_id UUID                     NOT NULL,
    body              TEXT                     NOT NULL,
    silent            BOOLEAN                  NOT NULL,
    send_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    -- Preenchido quando o envio falhou na hora marcada (bloqueio, saiu do grupo, só admins enviam).
    failed_at         TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_scheduled_messages PRIMARY KEY (id),
    CONSTRAINT fk_scheduled_messages_conversation FOREIGN KEY (conversation_id)
        REFERENCES conversations (id) ON DELETE CASCADE,
    CONSTRAINT fk_scheduled_messages_sender FOREIGN KEY (sender_id) REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT uq_scheduled_messages_client UNIQUE (sender_id, client_message_id),
    CONSTRAINT ck_scheduled_messages_body CHECK (char_length(body) BETWEEN 1 AND 65536)
);
CREATE INDEX ix_scheduled_messages_due ON scheduled_messages (send_at) WHERE failed_at IS NULL;
CREATE INDEX ix_scheduled_messages_owner ON scheduled_messages (sender_id, conversation_id);

CREATE TABLE message_drafts (
    user_id         UUID                     NOT NULL,
    conversation_id UUID                     NOT NULL,
    body            TEXT                     NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_message_drafts PRIMARY KEY (user_id, conversation_id),
    CONSTRAINT fk_message_drafts_user FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT fk_message_drafts_conversation FOREIGN KEY (conversation_id)
        REFERENCES conversations (id) ON DELETE CASCADE,
    CONSTRAINT ck_message_drafts_body CHECK (char_length(body) BETWEEN 1 AND 65536)
);
