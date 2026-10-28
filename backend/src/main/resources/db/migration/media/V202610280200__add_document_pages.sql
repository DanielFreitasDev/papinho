-- Páginas dos PDFs (MD-01), lidas ao completar o upload, para a bolha mostrar "5 páginas • PDF • 230 KB" com a
-- primeira página por cima, como no WhatsApp. A prévia usa a miniatura das imagens (has_thumbnail, thumbhash, e
-- width/height da própria prévia). Os PDFs enviados antes desta versão ficam marcados para o job que preenche em
-- segundo plano; os que não abrem (senha, arquivo corrompido) ficam com nulo e sem prévia.
ALTER TABLE media_files ADD COLUMN page_count INT;
ALTER TABLE media_files ADD COLUMN pages_pending BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE media_files ADD CONSTRAINT ck_media_files_page_count CHECK (page_count IS NULL OR page_count > 0);
UPDATE media_files SET pages_pending = TRUE WHERE content_type = 'application/pdf';
CREATE INDEX ix_media_files_pages_pending ON media_files (created_at) WHERE pages_pending;
