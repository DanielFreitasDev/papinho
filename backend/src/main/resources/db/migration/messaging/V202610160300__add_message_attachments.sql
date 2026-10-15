-- Mensagem com anexo (MD-01): um arquivo por mensagem, como no WhatsApp; o texto vira legenda e pode ficar
-- vazio. O tipo do anexo fica copiado aqui para a prévia da lista de conversas.
ALTER TABLE messages ADD COLUMN attachment_id UUID;
ALTER TABLE messages ADD COLUMN attachment_kind VARCHAR(16);
ALTER TABLE messages ADD CONSTRAINT fk_messages_attachment FOREIGN KEY (attachment_id) REFERENCES media_files (id);
ALTER TABLE messages DROP CONSTRAINT ck_messages_body_length;
ALTER TABLE messages ADD CONSTRAINT ck_messages_body_length
    CHECK (char_length(body) <= 65536 AND (char_length(body) >= 1 OR attachment_id IS NOT NULL));
ALTER TABLE messages ADD CONSTRAINT ck_messages_attachment_kind
    CHECK ((attachment_id IS NULL) = (attachment_kind IS NULL));
CREATE INDEX ix_messages_attachment_id ON messages (attachment_id);
