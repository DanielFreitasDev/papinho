-- Perfil público (ID-02): nome de exibição, recado e foto, separados do @usuario.
CREATE TABLE profiles (
    user_id      UUID                     NOT NULL,
    display_name VARCHAR(64)              NOT NULL,
    about        VARCHAR(140),
    photo_key    TEXT,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    version      BIGINT                   NOT NULL DEFAULT 0,
    CONSTRAINT pk_profiles PRIMARY KEY (user_id),
    CONSTRAINT fk_profiles_user FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT ck_profiles_display_name CHECK (length(btrim(display_name)) BETWEEN 1 AND 64)
);
