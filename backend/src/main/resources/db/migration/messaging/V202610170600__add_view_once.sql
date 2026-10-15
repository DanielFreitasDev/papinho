-- Visualização única (MS-08): o anexo só abre por um token de curta duração emitido uma vez por destinatário,
-- com Cache-Control: no-store. Depois que todos abriram (ou após 14 dias) o arquivo sai do servidor; o tipo
-- do anexo fica para o aviso "Foto aberta".
ALTER TABLE messages ADD COLUMN view_once BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE messages DROP CONSTRAINT ck_messages_attachment_kind;
ALTER TABLE messages ADD CONSTRAINT ck_messages_attachment_kind
    CHECK ((attachment_id IS NOT NULL AND attachment_kind IS NOT NULL)
        OR (attachment_id IS NULL AND (attachment_kind IS NULL OR view_once)));
ALTER TABLE messages DROP CONSTRAINT ck_messages_body_length;
ALTER TABLE messages ADD CONSTRAINT ck_messages_body_length
    CHECK (char_length(body) <= 65536
        AND (char_length(body) >= 1 OR attachment_id IS NOT NULL OR deleted_at IS NOT NULL OR view_once));
CREATE INDEX ix_messages_view_once ON messages (created_at) WHERE view_once AND attachment_id IS NOT NULL;

CREATE TABLE view_once_opens (
    message_id       UUID                     NOT NULL,
    user_id          UUID                     NOT NULL,
    token_hash       VARCHAR(64)              NOT NULL,
    opened_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    token_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_view_once_opens PRIMARY KEY (message_id, user_id),
    CONSTRAINT fk_view_once_opens_message FOREIGN KEY (message_id) REFERENCES messages (id) ON DELETE CASCADE,
    CONSTRAINT fk_view_once_opens_user FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT uq_view_once_opens_token UNIQUE (token_hash)
);
