-- GR-06: o stemmer portuguese não junta singular e plural em -ão/-ões/-ães nem em -m/-ns ("mensagens" fica
-- inteiro, "ações" vira outro radical que "ação"), ao contrário do que dizia o comentário da V202610190200.
-- Os plurais são trocados pelo singular antes do stemmer, no texto indexado e na consulta, com e sem acento.
-- Roda sobre o texto já sem acentos e em minúsculas (papinho_unaccent), então a troca só precisa de ASCII.
CREATE FUNCTION papinho_search_text(text) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
    RETURN regexp_replace(regexp_replace(papinho_unaccent($1), '(oe|ae|ao)s\M', 'ao', 'g'), 'ns\M', 'm', 'g');

DROP INDEX ix_messages_body_fts;
CREATE INDEX ix_messages_body_fts ON messages USING gin (to_tsvector('papinho_portuguese', papinho_search_text(body)));
