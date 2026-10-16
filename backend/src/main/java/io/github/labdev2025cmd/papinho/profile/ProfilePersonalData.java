package io.github.labdev2025cmd.papinho.profile;

import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import io.github.labdev2025cmd.papinho.shared.personal.PersonalDataSection;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Perfil: nome de exibição, recado e se há foto (BK-02). */
@Component
class ProfilePersonalData implements PersonalDataSection {

    private final NamedParameterJdbcTemplate jdbc;
    private final JsonMapper json;

    ProfilePersonalData(NamedParameterJdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public String name() {
        return "perfil";
    }

    @Override
    public JsonNode collect(UUID userId) {
        return PersonalDataSection.json(jdbc, json, """
                select jsonb_build_object('nome', p.display_name, 'recado', p.about,
                                          'temFoto', p.photo_key is not null, 'atualizadoEm', p.updated_at)
                  from profiles p where p.user_id = :userId
                """, userId);
    }
}
