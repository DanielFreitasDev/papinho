-- Busca de mensagens (GR-06): texto completo em português sem acentos (radicais: "mensagens" acha "mensagem") e
-- trigramas para trechos de palavra ("Fortal" acha "Fortaleza"). As extensões vêm no contrib do PostgreSQL.
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Configuração "portuguese" que ignora acentos antes de tirar o radical.
CREATE TEXT SEARCH CONFIGURATION papinho_portuguese (COPY = pg_catalog.portuguese);
ALTER TEXT SEARCH CONFIGURATION papinho_portuguese
    ALTER MAPPING FOR hword, hword_part, word WITH unaccent, portuguese_stem;

-- unaccent() é STABLE; o índice precisa de uma função IMMUTABLE com o dicionário fixo.
CREATE FUNCTION papinho_unaccent(text) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
    RETURN public.unaccent('public.unaccent'::regdictionary, lower($1));

CREATE INDEX ix_messages_body_fts ON messages USING gin (to_tsvector('papinho_portuguese', body));
CREATE INDEX ix_messages_body_trgm ON messages USING gin (papinho_unaccent(body) gin_trgm_ops);
