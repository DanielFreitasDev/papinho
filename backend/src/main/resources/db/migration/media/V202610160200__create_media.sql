-- Arquivos enviados (MD-01, MD-02, ADR 0006 e 0009). O conteúdo fica na porta de armazenamento; aqui só os
-- metadados. Uploads tus em andamento ficam em media_uploads até completar ou expirar.
CREATE TABLE media_uploads (
    id            UUID                     NOT NULL,
    owner_id      UUID                     NOT NULL,
    length        BIGINT                   NOT NULL,
    filename      VARCHAR(255)             NOT NULL,
    declared_type VARCHAR(127),
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_media_uploads PRIMARY KEY (id),
    CONSTRAINT fk_media_uploads_owner FOREIGN KEY (owner_id) REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT ck_media_uploads_length CHECK (length BETWEEN 1 AND 1073741824)
);
CREATE INDEX ix_media_uploads_owner_id ON media_uploads (owner_id);
CREATE INDEX ix_media_uploads_expires_at ON media_uploads (expires_at);

CREATE TABLE media_files (
    id            UUID                     NOT NULL,
    owner_id      UUID,
    kind          VARCHAR(16)              NOT NULL,
    content_type  VARCHAR(127)             NOT NULL,
    size          BIGINT                   NOT NULL,
    filename      VARCHAR(255)             NOT NULL,
    sha256        VARCHAR(64)              NOT NULL,
    width         INT,
    height        INT,
    thumbhash     VARCHAR(64),
    has_thumbnail BOOLEAN                  NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_media_files PRIMARY KEY (id),
    CONSTRAINT fk_media_files_owner FOREIGN KEY (owner_id) REFERENCES user_accounts (id) ON DELETE SET NULL,
    CONSTRAINT ck_media_files_kind CHECK (kind IN ('IMAGE', 'VIDEO', 'AUDIO', 'DOCUMENT')),
    CONSTRAINT ck_media_files_size CHECK (size BETWEEN 0 AND 1073741824)
);
CREATE INDEX ix_media_files_owner_id ON media_files (owner_id);
