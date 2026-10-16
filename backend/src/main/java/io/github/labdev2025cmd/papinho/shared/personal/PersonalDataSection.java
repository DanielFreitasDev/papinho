package io.github.labdev2025cmd.papinho.shared.personal;

import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Parte dos dados de uma pessoa para a portabilidade da LGPD (BK-02). Cada módulo descreve só os próprios dados;
 * o módulo de backup junta as seções num arquivo. Segredos (hashes, chaves, tokens) nunca entram.
 */
public interface PersonalDataSection {

    /** Nome da seção no arquivo exportado. */
    String name();

    JsonNode collect(UUID userId);

    /**
     * Executa uma consulta que devolve um único valor JSON (use {@code jsonb_agg}/{@code to_jsonb}); datas saem em
     * ISO 8601 pelo próprio PostgreSQL.
     */
    static JsonNode json(NamedParameterJdbcTemplate jdbc, JsonMapper json, String sql, UUID userId) {
        // Sem linha (preferência nunca salva, por exemplo) vira null na seção.
        java.util.List<String> rows = jdbc.queryForList(sql, new MapSqlParameterSource("userId", userId),
                String.class);
        String value = rows.isEmpty() ? null : rows.getFirst();
        return json.readTree(value == null ? "null" : value);
    }
}
