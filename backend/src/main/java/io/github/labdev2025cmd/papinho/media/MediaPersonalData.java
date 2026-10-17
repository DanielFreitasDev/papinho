package io.github.labdev2025cmd.papinho.media;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import io.github.labdev2025cmd.papinho.shared.personal.PersonalDataSection;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Arquivos que a pessoa enviou: nome, tipo, tamanho e data (BK-02). O conteúdo vai à parte, se pedido. */
@Component
class MediaPersonalData implements PersonalDataSection {

    private final NamedParameterJdbcTemplate jdbc;
    private final JsonMapper json;
    private final MediaService media;

    MediaPersonalData(NamedParameterJdbcTemplate jdbc, JsonMapper json, @Lazy MediaService media) {
        this.jdbc = jdbc;
        this.json = json;
        this.media = media;
    }

    @Override
    public String name() {
        return "arquivos";
    }

    @Override
    public JsonNode collect(UUID userId) {
        List<UUID> owned = jdbc.queryForList("select id from media_files where owner_id = :userId",
                new MapSqlParameterSource("userId", userId), UUID.class);
        Set<UUID> hidden = media.hiddenFromOwner(userId, owned);
        String value = jdbc.queryForObject("""
                select coalesce(jsonb_agg(jsonb_build_object('id', f.id, 'nome', f.filename, 'tipo', f.content_type,
                                                             'tamanho', f.size, 'enviadoEm', f.created_at)
                                          order by f.created_at), '[]')
                  from media_files f where f.owner_id = :userId and f.id not in (:hidden)
                """, new MapSqlParameterSource("userId", userId).addValue("hidden",
                hidden.isEmpty() ? List.of(new UUID(0, 0)) : hidden), String.class);
        return json.readTree(value == null ? "[]" : value);
    }
}
