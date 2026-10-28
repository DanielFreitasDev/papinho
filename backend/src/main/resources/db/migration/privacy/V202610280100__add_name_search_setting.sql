-- "Aparecer na busca por nome": quem desliga sai das sugestões de pessoas ao adicionar contato, mas continua sendo
-- achado pelo @usuario exato (como pelo link e pelo QR Code) e por quem já o tem nos contatos. Ligado por padrão.
ALTER TABLE privacy_settings ADD COLUMN name_search BOOLEAN NOT NULL DEFAULT TRUE;
