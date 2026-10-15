-- Conversas individuais, mensagens com seq por conversa e clientMessageId idempotente (ADR 0010),
-- confirmações de entrega e leitura, reações, mensagens fixadas e favoritas (MS-02, MS-04, MS-14, RT-01, RT-02).
CREATE TABLE conversations (
    id         UUID                     NOT NULL,
    kind       VARCHAR(16)              NOT NULL,
    -- Par ordenado "menor:maior" dos dois ids: uma única conversa individual por par de pessoas.
    direct_key VARCHAR(73),
    created_by UUID,
    last_seq   BIGINT                   NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_conversations PRIMARY KEY (id),
    CONSTRAINT uq_conversations_direct_key UNIQUE (direct_key),
    CONSTRAINT fk_conversations_created_by FOREIGN KEY (created_by) REFERENCES user_accounts (id) ON DELETE SET NULL,
    CONSTRAINT ck_conversations_kind CHECK (kind IN ('DIRECT'))
);

CREATE TABLE conversation_members (
    conversation_id UUID                     NOT NULL,
    user_id         UUID                     NOT NULL,
    -- ACTIVE: conversa normal. REQUEST: solicitação de mensagem ainda não aceita (CT-02). DECLINED: recusada.
    state           VARCHAR(16)              NOT NULL,
    delivered_seq   BIGINT                   NOT NULL DEFAULT 0,
    read_seq        BIGINT                   NOT NULL DEFAULT 0,
    marked_unread   BOOLEAN                  NOT NULL DEFAULT FALSE,
    joined_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_conversation_members PRIMARY KEY (conversation_id, user_id),
    CONSTRAINT fk_conversation_members_conversation FOREIGN KEY (conversation_id)
        REFERENCES conversations (id) ON DELETE CASCADE,
    CONSTRAINT fk_conversation_members_user FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT ck_conversation_members_state CHECK (state IN ('ACTIVE', 'REQUEST', 'DECLINED'))
);
CREATE INDEX ix_conversation_members_user_id ON conversation_members (user_id);

-- Cada avanço do ponteiro de entrega ou leitura vira uma linha: o "Dados da mensagem" sabe quando cada
-- mensagem foi entregue e lida sem uma linha por mensagem e destinatário.
CREATE TABLE conversation_receipts (
    conversation_id UUID                     NOT NULL,
    user_id         UUID                     NOT NULL,
    kind            VARCHAR(16)              NOT NULL,
    up_to_seq       BIGINT                   NOT NULL,
    at              TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_conversation_receipts PRIMARY KEY (conversation_id, user_id, kind, up_to_seq),
    CONSTRAINT fk_conversation_receipts_member FOREIGN KEY (conversation_id, user_id)
        REFERENCES conversation_members (conversation_id, user_id) ON DELETE CASCADE,
    CONSTRAINT ck_conversation_receipts_kind CHECK (kind IN ('DELIVERED', 'READ'))
);

CREATE TABLE messages (
    id                UUID                     NOT NULL,
    conversation_id   UUID                     NOT NULL,
    seq               BIGINT                   NOT NULL,
    -- seq da última alteração (criação, reação, fixar): o cliente sincroniza por change_seq > afterSeq.
    change_seq        BIGINT                   NOT NULL,
    sender_id         UUID,
    client_message_id UUID                     NOT NULL,
    body              TEXT                     NOT NULL,
    reply_to_id       UUID,
    forward_count     INT                      NOT NULL DEFAULT 0,
    pinned_at         TIMESTAMP WITH TIME ZONE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_messages PRIMARY KEY (id),
    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE,
    CONSTRAINT fk_messages_sender FOREIGN KEY (sender_id) REFERENCES user_accounts (id) ON DELETE SET NULL,
    CONSTRAINT fk_messages_reply_to FOREIGN KEY (reply_to_id) REFERENCES messages (id) ON DELETE SET NULL,
    CONSTRAINT uq_messages_conversation_seq UNIQUE (conversation_id, seq),
    CONSTRAINT uq_messages_sender_client_message_id UNIQUE (sender_id, client_message_id),
    CONSTRAINT ck_messages_body_length CHECK (char_length(body) BETWEEN 1 AND 65536),
    CONSTRAINT ck_messages_forward_count CHECK (forward_count >= 0)
);
CREATE INDEX ix_messages_conversation_change_seq ON messages (conversation_id, change_seq);
CREATE INDEX ix_messages_reply_to_id ON messages (reply_to_id);

CREATE TABLE message_reactions (
    message_id UUID                     NOT NULL,
    user_id    UUID                     NOT NULL,
    emoji      VARCHAR(32)              NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_message_reactions PRIMARY KEY (message_id, user_id),
    CONSTRAINT fk_message_reactions_message FOREIGN KEY (message_id) REFERENCES messages (id) ON DELETE CASCADE,
    CONSTRAINT fk_message_reactions_user FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE
);

CREATE TABLE message_stars (
    user_id    UUID                     NOT NULL,
    message_id UUID                     NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_message_stars PRIMARY KEY (user_id, message_id),
    CONSTRAINT fk_message_stars_user FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT fk_message_stars_message FOREIGN KEY (message_id) REFERENCES messages (id) ON DELETE CASCADE
);
CREATE INDEX ix_message_stars_message_id ON message_stars (message_id);
