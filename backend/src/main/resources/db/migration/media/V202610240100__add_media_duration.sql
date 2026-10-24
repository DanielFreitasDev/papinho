-- Duração dos áudios (AU-02), lida do arquivo ao completar o upload, para o cliente mostrar o tempo antes de
-- tocar. Os áudios enviados antes desta versão ficam marcados para o job que preenche em segundo plano; os que
-- não dizem a duração ficam com nulo e o cliente usa a do tocador.
ALTER TABLE media_files ADD COLUMN duration_ms BIGINT;
ALTER TABLE media_files ADD COLUMN duration_pending BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE media_files ADD CONSTRAINT ck_media_files_duration CHECK (duration_ms IS NULL OR duration_ms >= 0);
UPDATE media_files SET duration_pending = TRUE WHERE kind = 'AUDIO';
CREATE INDEX ix_media_files_duration_pending ON media_files (created_at) WHERE duration_pending;
