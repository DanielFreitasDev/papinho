-- Enquetes (MS-09): a mensagem (kind POLL) guarda a pergunta no corpo; opções e votos ficam ao lado. Votar
-- avança o change_seq da mensagem, e os resultados chegam a todos pelo afterSeq (tempo real).
ALTER TABLE messages DROP CONSTRAINT ck_messages_kind;
ALTER TABLE messages ADD CONSTRAINT ck_messages_kind CHECK (kind IN ('USER', 'SYSTEM', 'POLL'));

CREATE TABLE polls (
    message_id UUID                     NOT NULL,
    multiple   BOOLEAN                  NOT NULL,
    anonymous  BOOLEAN                  NOT NULL,
    closes_at  TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_polls PRIMARY KEY (message_id),
    CONSTRAINT fk_polls_message FOREIGN KEY (message_id) REFERENCES messages (id) ON DELETE CASCADE
);

CREATE TABLE poll_options (
    id         UUID         NOT NULL,
    message_id UUID         NOT NULL,
    position   INT          NOT NULL,
    text       VARCHAR(100) NOT NULL,
    CONSTRAINT pk_poll_options PRIMARY KEY (id),
    CONSTRAINT fk_poll_options_poll FOREIGN KEY (message_id) REFERENCES polls (message_id) ON DELETE CASCADE,
    CONSTRAINT uq_poll_options_position UNIQUE (message_id, position),
    CONSTRAINT ck_poll_options_text CHECK (char_length(btrim(text)) BETWEEN 1 AND 100)
);

CREATE TABLE poll_votes (
    option_id  UUID                     NOT NULL,
    user_id    UUID                     NOT NULL,
    message_id UUID                     NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_poll_votes PRIMARY KEY (option_id, user_id),
    CONSTRAINT fk_poll_votes_option FOREIGN KEY (option_id) REFERENCES poll_options (id) ON DELETE CASCADE,
    CONSTRAINT fk_poll_votes_user FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT fk_poll_votes_poll FOREIGN KEY (message_id) REFERENCES polls (message_id) ON DELETE CASCADE
);
CREATE INDEX ix_poll_votes_message_id ON poll_votes (message_id);
