-- Registro de publicação de eventos do Spring Modulith (outbox, ADR 0005).
-- Colunas espelham JpaEventPublication de spring-modulith-events-jpa 2.1.1.
CREATE TABLE event_publication (
    id                     UUID                     NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    listener_id            TEXT                     NOT NULL,
    serialized_event       TEXT                     NOT NULL,
    event_type             TEXT                     NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    completion_attempts    INT                      NOT NULL DEFAULT 0,
    status                 TEXT,
    CONSTRAINT pk_event_publication PRIMARY KEY (id)
);

CREATE INDEX ix_event_publication_serialized_event_hash ON event_publication USING hash (serialized_event);
CREATE INDEX ix_event_publication_completion_date ON event_publication (completion_date);

CREATE TABLE event_publication_archive (
    id                     UUID                     NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    listener_id            TEXT                     NOT NULL,
    serialized_event       TEXT                     NOT NULL,
    event_type             TEXT                     NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    completion_attempts    INT                      NOT NULL DEFAULT 0,
    status                 TEXT,
    CONSTRAINT pk_event_publication_archive PRIMARY KEY (id)
);
