-- Limpeza dos arquivos enviados e nunca usados (MD-01): in_use fica verdadeiro quando a limpeza confirma que
-- algum módulo usa o arquivo (anexo, figurinha, foto de grupo), para não conferir de novo a cada rodada. Os que
-- ninguém usa depois do prazo saem do disco e devolvem a cota.
ALTER TABLE media_files ADD COLUMN in_use BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX ix_media_files_unchecked ON media_files (created_at) WHERE NOT in_use;
