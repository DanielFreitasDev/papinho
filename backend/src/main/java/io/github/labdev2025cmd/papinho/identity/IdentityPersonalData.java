package io.github.labdev2025cmd.papinho.identity;

import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import io.github.labdev2025cmd.papinho.shared.personal.PersonalDataSection;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Conta, passkeys (só nome e datas) e convites criados (BK-02); nada de senha, TOTP ou chaves. */
@Component
class IdentityPersonalData implements PersonalDataSection {

    private final NamedParameterJdbcTemplate jdbc;
    private final JsonMapper json;

    IdentityPersonalData(NamedParameterJdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public String name() {
        return "conta";
    }

    @Override
    public JsonNode collect(UUID userId) {
        return PersonalDataSection.json(jdbc, json, """
                select jsonb_build_object(
                  'id', u.id, 'usuario', u.username, 'papel', u.role, 'criadaEm', u.created_at,
                  'usuarioTrocadoEm', u.username_changed_at, 'totpLigado', u.totp_enabled,
                  'passkeys', coalesce((select jsonb_agg(jsonb_build_object('nome', c.label, 'criadaEm', c.created,
                                                                           'usadaEm', c.last_used))
                                          from user_credentials c join user_entities e on e.id = c.user_entity_user_id
                                         where e.name = cast(:userId as text)), '[]'),
                  'convitesCriados', coalesce((select jsonb_agg(jsonb_build_object('criadoEm', i.created_at,
                                                                                  'expiraEm', i.expires_at,
                                                                                  'usos', i.uses, 'maxUsos', i.max_uses,
                                                                                  'revogadoEm', i.revoked_at))
                                                 from invites i where i.created_by = :userId), '[]'))
                  from user_accounts u where u.id = :userId
                """, userId);
    }
}
