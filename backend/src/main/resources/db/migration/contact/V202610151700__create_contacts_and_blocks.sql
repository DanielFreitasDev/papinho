-- Contatos com apelido local (CT-01) e bloqueios (CT-03).
CREATE TABLE contacts (
    owner_id   UUID                     NOT NULL,
    contact_id UUID                     NOT NULL,
    nickname   VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_contacts PRIMARY KEY (owner_id, contact_id),
    CONSTRAINT fk_contacts_owner FOREIGN KEY (owner_id) REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT fk_contacts_contact FOREIGN KEY (contact_id) REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT ck_contacts_not_self CHECK (owner_id <> contact_id)
);
CREATE INDEX ix_contacts_contact_id ON contacts (contact_id);

CREATE TABLE blocks (
    blocker_id UUID                     NOT NULL,
    blocked_id UUID                     NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_blocks PRIMARY KEY (blocker_id, blocked_id),
    CONSTRAINT fk_blocks_blocker FOREIGN KEY (blocker_id) REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT fk_blocks_blocked FOREIGN KEY (blocked_id) REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT ck_blocks_not_self CHECK (blocker_id <> blocked_id)
);
CREATE INDEX ix_blocks_blocked_id ON blocks (blocked_id);
