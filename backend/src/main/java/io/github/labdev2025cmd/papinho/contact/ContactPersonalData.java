package io.github.labdev2025cmd.papinho.contact;

import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import io.github.labdev2025cmd.papinho.shared.personal.PersonalDataSection;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Contatos (com o apelido local) e bloqueados (BK-02). */
@Component
class ContactPersonalData implements PersonalDataSection {

    private final NamedParameterJdbcTemplate jdbc;
    private final JsonMapper json;

    ContactPersonalData(NamedParameterJdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public String name() {
        return "contatos";
    }

    @Override
    public JsonNode collect(UUID userId) {
        return PersonalDataSection.json(jdbc, json, """
                select jsonb_build_object(
                  'contatos', coalesce((select jsonb_agg(jsonb_build_object('usuario', u.username,
                                                                           'apelido', c.nickname,
                                                                           'adicionadoEm', c.created_at)
                                                         order by u.username)
                                          from contacts c join user_accounts u on u.id = c.contact_id
                                         where c.owner_id = :userId), '[]'),
                  'bloqueados', coalesce((select jsonb_agg(jsonb_build_object('usuario', u.username,
                                                                             'desde', b.created_at))
                                            from blocks b join user_accounts u on u.id = b.blocked_id
                                           where b.blocker_id = :userId), '[]'))
                """, userId);
    }
}
