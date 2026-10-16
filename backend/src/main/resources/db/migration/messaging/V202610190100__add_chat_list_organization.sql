-- Organização da lista de conversas de cada pessoa (GR-05): arquivar, fixar, silenciar, favoritar e pastas.
-- Tudo é por participante: nada disso aparece para os outros membros da conversa.

-- Arquivada até o seq em que foi arquivada: uma mensagem nova de outra pessoa a tira do arquivo, a menos que a
-- pessoa tenha ligado "manter arquivadas" (messaging_preferences.keep_archived).
ALTER TABLE conversation_members ADD COLUMN archived_seq BIGINT;
ALTER TABLE conversation_members ADD COLUMN pinned_at TIMESTAMP WITH TIME ZONE;
-- "Sempre" é 9999-12-31; vencido conta como não silenciada.
ALTER TABLE conversation_members ADD COLUMN muted_until TIMESTAMP WITH TIME ZONE;
ALTER TABLE conversation_members ADD COLUMN mute_mentions BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE conversation_members ADD COLUMN favorite BOOLEAN NOT NULL DEFAULT FALSE;
-- "Apagar conversa": some da lista até chegar mensagem com seq maior.
ALTER TABLE conversation_members ADD COLUMN removed_seq BIGINT;
CREATE INDEX ix_conversation_members_pinned ON conversation_members (user_id) WHERE pinned_at IS NOT NULL;

ALTER TABLE messaging_preferences ADD COLUMN keep_archived BOOLEAN NOT NULL DEFAULT FALSE;

-- Listas personalizadas (pastas) de cada pessoa.
CREATE TABLE chat_folders (
    id         UUID                     NOT NULL,
    user_id    UUID                     NOT NULL,
    name       VARCHAR(40)              NOT NULL,
    position   INT                      NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_chat_folders PRIMARY KEY (id),
    CONSTRAINT fk_chat_folders_user FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT ck_chat_folders_name CHECK (char_length(btrim(name)) BETWEEN 1 AND 40)
);
CREATE INDEX ix_chat_folders_user_id ON chat_folders (user_id);

CREATE TABLE chat_folder_items (
    folder_id       UUID NOT NULL,
    conversation_id UUID NOT NULL,
    CONSTRAINT pk_chat_folder_items PRIMARY KEY (folder_id, conversation_id),
    CONSTRAINT fk_chat_folder_items_folder FOREIGN KEY (folder_id) REFERENCES chat_folders (id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_folder_items_conversation FOREIGN KEY (conversation_id)
        REFERENCES conversations (id) ON DELETE CASCADE
);
CREATE INDEX ix_chat_folder_items_conversation_id ON chat_folder_items (conversation_id);
