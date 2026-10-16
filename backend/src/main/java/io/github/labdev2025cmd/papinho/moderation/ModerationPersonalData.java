package io.github.labdev2025cmd.papinho.moderation;

import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import io.github.labdev2025cmd.papinho.shared.personal.PersonalDataSection;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Denúncias que a pessoa fez (BK-02); as feitas contra ela ficam com a moderação. */
@Component
class ModerationPersonalData implements PersonalDataSection {

    private final NamedParameterJdbcTemplate jdbc;
    private final JsonMapper json;

    ModerationPersonalData(NamedParameterJdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public String name() {
        return "denuncias";
    }

    @Override
    public JsonNode collect(UUID userId) {
        return PersonalDataSection.json(jdbc, json, """
                select coalesce(jsonb_agg(jsonb_build_object('usuario', u.username, 'motivo', r.reason,
                                                             'situacao', r.status, 'em', r.created_at)), '[]')
                  from reports r left join user_accounts u on u.id = r.reported_id
                 where r.reporter_id = :userId
                """, userId);
    }
}
