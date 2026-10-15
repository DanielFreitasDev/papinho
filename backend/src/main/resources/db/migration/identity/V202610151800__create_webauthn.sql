-- Passkeys (ID-04) no esquema que o JdbcPublicKeyCredentialUserEntityRepository e o JdbcUserCredentialRepository
-- do spring-security-webauthn 7.1 esperam. user_entities.name é o id da conta (nome do principal).
CREATE TABLE user_entities (
    id           VARCHAR(1000) NOT NULL,
    name         VARCHAR(100)  NOT NULL,
    display_name VARCHAR(200),
    CONSTRAINT pk_user_entities PRIMARY KEY (id),
    CONSTRAINT uq_user_entities_name UNIQUE (name)
);

CREATE TABLE user_credentials (
    credential_id                VARCHAR(1000)            NOT NULL,
    user_entity_user_id          VARCHAR(1000)            NOT NULL,
    public_key                   BYTEA                    NOT NULL,
    signature_count              BIGINT,
    uv_initialized               BOOLEAN,
    backup_eligible              BOOLEAN                  NOT NULL,
    authenticator_transports     VARCHAR(1000),
    public_key_credential_type   VARCHAR(100),
    backup_state                 BOOLEAN                  NOT NULL,
    attestation_object           BYTEA,
    attestation_client_data_json BYTEA,
    created                      TIMESTAMP,
    last_used                    TIMESTAMP,
    label                        VARCHAR(1000)            NOT NULL,
    CONSTRAINT pk_user_credentials PRIMARY KEY (credential_id),
    CONSTRAINT fk_user_credentials_user_entity FOREIGN KEY (user_entity_user_id)
        REFERENCES user_entities (id) ON DELETE CASCADE
);
CREATE INDEX ix_user_credentials_user_entity_user_id ON user_credentials (user_entity_user_id);
