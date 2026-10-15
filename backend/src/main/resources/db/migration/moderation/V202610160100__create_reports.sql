-- Denúncias de usuários (CT-04): cópia das últimas mensagens da conversa, tirada no momento da denúncia,
-- para a fila de moderação do admin (AD-01). A cópia continua lá mesmo que as mensagens sejam apagadas.
CREATE TABLE reports (
    id              UUID                     NOT NULL,
    reporter_id     UUID,
    reported_id     UUID                     NOT NULL,
    conversation_id UUID,
    reason          VARCHAR(500),
    status          VARCHAR(16)              NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    resolved_at     TIMESTAMP WITH TIME ZONE,
    resolved_by     UUID,
    CONSTRAINT pk_reports PRIMARY KEY (id),
    CONSTRAINT fk_reports_reporter FOREIGN KEY (reporter_id) REFERENCES user_accounts (id) ON DELETE SET NULL,
    CONSTRAINT fk_reports_reported FOREIGN KEY (reported_id) REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT fk_reports_resolved_by FOREIGN KEY (resolved_by) REFERENCES user_accounts (id) ON DELETE SET NULL,
    CONSTRAINT ck_reports_status CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED'))
);
CREATE INDEX ix_reports_status_created_at ON reports (status, created_at);
CREATE INDEX ix_reports_reported_id ON reports (reported_id);

CREATE TABLE report_messages (
    report_id  UUID                     NOT NULL,
    position   INT                      NOT NULL,
    message_id UUID                     NOT NULL,
    sender_id  UUID,
    body       TEXT                     NOT NULL,
    sent_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_report_messages PRIMARY KEY (report_id, position),
    CONSTRAINT fk_report_messages_report FOREIGN KEY (report_id) REFERENCES reports (id) ON DELETE CASCADE
);
