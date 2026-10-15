-- Editar (MS-05) e apagar (MS-06). Apagar para todos vira um "túmulo": a linha fica para manter o seq e o aviso
-- "Esta mensagem foi apagada", mas o texto e o anexo são removidos.
ALTER TABLE messages ADD COLUMN edited_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE messages ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE messages DROP CONSTRAINT ck_messages_body_length;
ALTER TABLE messages ADD CONSTRAINT ck_messages_body_length
    CHECK (char_length(body) <= 65536
        AND (char_length(body) >= 1 OR attachment_id IS NOT NULL OR deleted_at IS NOT NULL));
ALTER TABLE messages ADD CONSTRAINT ck_messages_deleted_is_empty
    CHECK (deleted_at IS NULL OR (char_length(body) = 0 AND attachment_id IS NULL));

-- "Apagar para mim": some só para quem apagou, em todos os dispositivos dela.
CREATE TABLE message_hidden (
    user_id    UUID                     NOT NULL,
    message_id UUID                     NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_message_hidden PRIMARY KEY (user_id, message_id),
    CONSTRAINT fk_message_hidden_user FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT fk_message_hidden_message FOREIGN KEY (message_id) REFERENCES messages (id) ON DELETE CASCADE
);
CREATE INDEX ix_message_hidden_message_id ON message_hidden (message_id);
