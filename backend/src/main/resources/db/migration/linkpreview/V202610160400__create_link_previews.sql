-- Prévias de link (MD-04, MD-05) em cache por URL: buscadas uma vez pelo servidor e servidas a todos. Falhas
-- também ficam guardadas (available = false) para não repetir a busca a cada mensagem.
CREATE TABLE link_previews (
    url_hash       VARCHAR(64)              NOT NULL,
    url            TEXT                     NOT NULL,
    available      BOOLEAN                  NOT NULL,
    title          VARCHAR(300),
    description    VARCHAR(1000),
    site_name      VARCHAR(200),
    image          BYTEA,
    embed_provider VARCHAR(16),
    embed_src      VARCHAR(500),
    fetched_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_link_previews PRIMARY KEY (url_hash),
    CONSTRAINT ck_link_previews_embed_provider CHECK (embed_provider IN ('YOUTUBE', 'VIMEO', 'SPOTIFY'))
);
