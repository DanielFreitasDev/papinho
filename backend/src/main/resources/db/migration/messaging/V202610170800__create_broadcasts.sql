-- Listas de transmissão (GR-03): a conversa da lista tem só o dono; cada envio vira uma mensagem na conversa
-- individual com cada destinatário que tem o dono nos contatos. Temporárias (GR-04): a lista expira numa data
-- ou as mensagens enviadas por ela são temporárias.
ALTER TABLE conversations DROP CONSTRAINT ck_conversations_kind;
ALTER TABLE conversations ADD CONSTRAINT ck_conversations_kind CHECK (kind IN ('DIRECT', 'GROUP', 'SELF', 'BROADCAST'));

CREATE TABLE broadcast_details (
    conversation_id     UUID                     NOT NULL,
    name                VARCHAR(100)             NOT NULL,
    expires_at          TIMESTAMP WITH TIME ZONE,
    message_ttl_seconds INT,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_broadcast_details PRIMARY KEY (conversation_id),
    CONSTRAINT fk_broadcast_details_conversation FOREIGN KEY (conversation_id)
        REFERENCES conversations (id) ON DELETE CASCADE,
    CONSTRAINT ck_broadcast_details_name CHECK (char_length(btrim(name)) BETWEEN 1 AND 100),
    CONSTRAINT ck_broadcast_details_ttl
        CHECK (message_ttl_seconds IS NULL OR message_ttl_seconds IN (86400, 604800, 7776000))
);

CREATE TABLE broadcast_recipients (
    conversation_id UUID NOT NULL,
    user_id         UUID NOT NULL,
    CONSTRAINT pk_broadcast_recipients PRIMARY KEY (conversation_id, user_id),
    CONSTRAINT fk_broadcast_recipients_list FOREIGN KEY (conversation_id)
        REFERENCES broadcast_details (conversation_id) ON DELETE CASCADE,
    CONSTRAINT fk_broadcast_recipients_user FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE
);
