-- Figurinhas (MS-10): pacotes enviados pelos usuários da instância, em WebP (inclusive animadas), visíveis para
-- todos; favoritas por pessoa. A mensagem de figurinha aponta para a figurinha e não tem texto. Tirar uma
-- figurinha (ou o pacote) só a esconde da biblioteca: as mensagens já enviadas continuam mostrando.
CREATE TABLE sticker_packs (
    id         UUID                     NOT NULL,
    owner_id   UUID,
    name       VARCHAR(64)              NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    removed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_sticker_packs PRIMARY KEY (id),
    CONSTRAINT fk_sticker_packs_owner FOREIGN KEY (owner_id) REFERENCES user_accounts (id) ON DELETE SET NULL,
    CONSTRAINT ck_sticker_packs_name CHECK (char_length(btrim(name)) BETWEEN 1 AND 64)
);

CREATE TABLE stickers (
    id         UUID                     NOT NULL,
    pack_id    UUID                     NOT NULL,
    media_id   UUID                     NOT NULL,
    emoji      VARCHAR(32),
    position   INT                      NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    removed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_stickers PRIMARY KEY (id),
    CONSTRAINT fk_stickers_pack FOREIGN KEY (pack_id) REFERENCES sticker_packs (id) ON DELETE CASCADE,
    CONSTRAINT fk_stickers_media FOREIGN KEY (media_id) REFERENCES media_files (id),
    CONSTRAINT uq_stickers_media UNIQUE (media_id)
);
CREATE INDEX ix_stickers_pack_id ON stickers (pack_id, position);

CREATE TABLE sticker_favorites (
    user_id    UUID                     NOT NULL,
    sticker_id UUID                     NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_sticker_favorites PRIMARY KEY (user_id, sticker_id),
    CONSTRAINT fk_sticker_favorites_user FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT fk_sticker_favorites_sticker FOREIGN KEY (sticker_id) REFERENCES stickers (id) ON DELETE CASCADE
);

ALTER TABLE messages ADD COLUMN sticker_id UUID;
ALTER TABLE messages ADD CONSTRAINT fk_messages_sticker FOREIGN KEY (sticker_id) REFERENCES stickers (id);
CREATE INDEX ix_messages_sticker_id ON messages (sticker_id);
ALTER TABLE messages DROP CONSTRAINT ck_messages_body_length;
ALTER TABLE messages ADD CONSTRAINT ck_messages_body_length
    CHECK (char_length(body) <= 65536
        AND (char_length(body) >= 1 OR attachment_id IS NOT NULL OR deleted_at IS NOT NULL OR view_once
             OR sticker_id IS NOT NULL));
