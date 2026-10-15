-- Transcrição de áudios (AU-03, AU-05). Provedores de servidor compatíveis com a API da OpenAI, configurados
-- pelo admin (chave cifrada em repouso); preferência de cada usuário; transcrições em cache por arquivo para
-- os demais membros da conversa.
CREATE TABLE transcription_providers (
    id                 UUID                     NOT NULL,
    kind               VARCHAR(16)              NOT NULL,
    name               VARCHAR(80)              NOT NULL,
    base_url           VARCHAR(500)             NOT NULL,
    model              VARCHAR(120)             NOT NULL,
    api_key_encrypted  TEXT,
    hotwords           VARCHAR(1000),
    max_bytes          BIGINT                   NOT NULL,
    enabled            BOOLEAN                  NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_transcription_providers PRIMARY KEY (id),
    CONSTRAINT ck_transcription_providers_kind CHECK (kind IN ('GROQ', 'WHISPER', 'OPENAI', 'CUSTOM')),
    CONSTRAINT ck_transcription_providers_max_bytes CHECK (max_bytes > 0)
);

-- Padrão da instância: NULL = no navegador (Whisper com Transformers.js, AU-04).
CREATE TABLE transcription_settings (
    id                  SMALLINT NOT NULL,
    default_provider_id UUID,
    CONSTRAINT pk_transcription_settings PRIMARY KEY (id),
    CONSTRAINT ck_transcription_settings_singleton CHECK (id = 1),
    CONSTRAINT fk_transcription_settings_provider FOREIGN KEY (default_provider_id)
        REFERENCES transcription_providers (id) ON DELETE SET NULL
);
INSERT INTO transcription_settings (id, default_provider_id) VALUES (1, NULL);

CREATE TABLE transcription_preferences (
    user_id     UUID        NOT NULL,
    -- 'DEFAULT' (o da instância), 'BROWSER' ou o id de um provedor.
    provider    VARCHAR(40) NOT NULL,
    auto        BOOLEAN     NOT NULL,
    CONSTRAINT pk_transcription_preferences PRIMARY KEY (user_id),
    CONSTRAINT fk_transcription_preferences_user FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE
);

CREATE TABLE transcripts (
    media_id   UUID                     NOT NULL,
    text       TEXT                     NOT NULL,
    source     VARCHAR(40)              NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_transcripts PRIMARY KEY (media_id),
    CONSTRAINT fk_transcripts_media FOREIGN KEY (media_id) REFERENCES media_files (id) ON DELETE CASCADE,
    CONSTRAINT ck_transcripts_text_length CHECK (char_length(text) <= 100000)
);
