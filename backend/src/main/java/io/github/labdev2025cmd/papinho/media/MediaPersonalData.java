package io.github.labdev2025cmd.papinho.media;

import java.util.UUID;

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

    MediaPersonalData(NamedParameterJdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public String name() {
        return "arquivos";
    }

    @Override
    public JsonNode collect(UUID userId) {
        return PersonalDataSection.json(jdbc, json, """
                select coalesce(jsonb_agg(jsonb_build_object('id', f.id, 'nome', f.filename, 'tipo', f.content_type,
                                                             'tamanho', f.size, 'enviadoEm', f.created_at)
                                          order by f.created_at), '[]')
                  from media_files f where f.owner_id = :userId
                """, userId);
    }
}
