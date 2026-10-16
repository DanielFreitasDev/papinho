package io.github.labdev2025cmd.papinho.privacy;

import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import io.github.labdev2025cmd.papinho.shared.personal.PersonalDataSection;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Configurações de privacidade, exceções e bloqueio de tela, sem os segredos (BK-02). */
@Component
class PrivacyPersonalData implements PersonalDataSection {

    private final NamedParameterJdbcTemplate jdbc;
    private final JsonMapper json;

    PrivacyPersonalData(NamedParameterJdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public String name() {
        return "privacidade";
    }

    @Override
    public JsonNode collect(UUID userId) {
        return PersonalDataSection.json(jdbc, json, """
                select jsonb_build_object(
                  'configuracoes', (select to_jsonb(s) - 'user_id' from privacy_settings s where s.user_id = :userId),
                  'excecoes', coalesce((select jsonb_agg(jsonb_build_object('item', e.setting, 'usuario', u.username))
                                          from privacy_exceptions e join user_accounts u on u.id = e.excluded_id
                                         where e.user_id = :userId), '[]'),
                  'bloqueioDeTela', (select jsonb_build_object('tipo', l.secret_kind, 'minutosSemUso', l.idle_minutes,
                                                               'aoEsconderAba', l.lock_on_hidden, 'atalho', l.shortcut,
                                                               'passkey', l.allow_passkey)
                                       from screen_locks l where l.user_id = :userId),
                  'conversasTrancadas', (select count(*) from locked_conversations c where c.user_id = :userId))
                """, userId);
    }
}
