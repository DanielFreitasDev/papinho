-- Travas do ShedLock: cada job agendado roda em uma instância por vez (MS-07).
CREATE TABLE shedlock (
    name       VARCHAR(64)              NOT NULL,
    lock_until TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_by  VARCHAR(255)             NOT NULL,
    CONSTRAINT pk_shedlock PRIMARY KEY (name)
);
