-- Visto por último (RT-03). O "online" fica só na memória do processo, a partir dos WebSockets ativos.
CREATE TABLE user_presence (
    user_id      UUID                     NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_user_presence PRIMARY KEY (user_id),
    CONSTRAINT fk_user_presence_user FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE
);
