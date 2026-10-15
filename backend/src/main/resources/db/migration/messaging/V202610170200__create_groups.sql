-- Grupos (GR-01) com convite por link e aprovação de entrada (GR-02), mensagens salvas (MS-12) e avisos do
-- sistema na conversa ("Ana adicionou Bruno").
ALTER TABLE conversations DROP CONSTRAINT ck_conversations_kind;
ALTER TABLE conversations ADD CONSTRAINT ck_conversations_kind CHECK (kind IN ('DIRECT', 'GROUP', 'SELF'));

CREATE TABLE group_details (
    conversation_id          UUID                     NOT NULL,
    name                     VARCHAR(100)             NOT NULL,
    description              VARCHAR(2048)            NOT NULL DEFAULT '',
    photo_media_id           UUID,
    -- Só admins enviam, editam os dados e adicionam membros; padrões do WhatsApp (todos podem).
    only_admins_send         BOOLEAN                  NOT NULL DEFAULT FALSE,
    only_admins_edit_info    BOOLEAN                  NOT NULL DEFAULT FALSE,
    only_admins_add_members  BOOLEAN                  NOT NULL DEFAULT FALSE,
    -- Quem entra pelo link espera a aprovação de um admin (GR-02).
    join_approval            BOOLEAN                  NOT NULL DEFAULT FALSE,
    invite_code              VARCHAR(32),
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_group_details PRIMARY KEY (conversation_id),
    CONSTRAINT fk_group_details_conversation FOREIGN KEY (conversation_id)
        REFERENCES conversations (id) ON DELETE CASCADE,
    CONSTRAINT fk_group_details_photo FOREIGN KEY (photo_media_id) REFERENCES media_files (id) ON DELETE SET NULL,
    CONSTRAINT uq_group_details_invite_code UNIQUE (invite_code),
    CONSTRAINT ck_group_details_name CHECK (char_length(btrim(name)) BETWEEN 1 AND 100)
);
CREATE INDEX ix_group_details_photo_media_id ON group_details (photo_media_id);

-- LEFT: saiu ou foi removido (não vê mais a conversa). PENDING: pediu para entrar pelo link (GR-02).
ALTER TABLE conversation_members ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'MEMBER';
ALTER TABLE conversation_members ADD CONSTRAINT ck_conversation_members_role CHECK (role IN ('MEMBER', 'ADMIN'));
ALTER TABLE conversation_members DROP CONSTRAINT ck_conversation_members_state;
ALTER TABLE conversation_members ADD CONSTRAINT ck_conversation_members_state
    CHECK (state IN ('ACTIVE', 'REQUEST', 'DECLINED', 'LEFT', 'PENDING'));

-- USER: mensagem de alguém. SYSTEM: aviso da conversa; o corpo é um JSON pequeno que o cliente traduz.
ALTER TABLE messages ADD COLUMN kind VARCHAR(16) NOT NULL DEFAULT 'USER';
ALTER TABLE messages ADD CONSTRAINT ck_messages_kind CHECK (kind IN ('USER', 'SYSTEM'));
