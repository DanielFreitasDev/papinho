-- Configurações da instância (AD-02): uma única linha com padrões seguros.
CREATE TABLE instance_settings (
    id                            SMALLINT    NOT NULL,
    registration_mode             TEXT        NOT NULL,
    username_change_cooldown_days INT         NOT NULL,
    message_edit_window_minutes   INT         NOT NULL,
    message_delete_window_hours   INT         NOT NULL,
    user_quota_bytes              BIGINT      NOT NULL,
    breached_password_check       BOOLEAN     NOT NULL,
    updated_at                    TIMESTAMP WITH TIME ZONE NOT NULL,
    version                       BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_instance_settings PRIMARY KEY (id),
    CONSTRAINT ck_instance_settings_singleton CHECK (id = 1),
    CONSTRAINT ck_instance_settings_registration_mode CHECK (registration_mode IN ('OPEN', 'INVITE_ONLY', 'CLOSED'))
);

INSERT INTO instance_settings (id, registration_mode, username_change_cooldown_days, message_edit_window_minutes,
                               message_delete_window_hours, user_quota_bytes, breached_password_check, updated_at)
VALUES (1, 'INVITE_ONLY', 30, 15, 48, 5368709120, FALSE, now());
