package io.github.labdev2025cmd.papinho.notification;

import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import io.github.labdev2025cmd.papinho.shared.personal.PersonalDataSection;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Preferências de notificação e os navegadores assinados (só o serviço e as datas; BK-02). */
@Component
class NotificationPersonalData implements PersonalDataSection {

    private final NamedParameterJdbcTemplate jdbc;
    private final JsonMapper json;

    NotificationPersonalData(NamedParameterJdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public String name() {
        return "notificacoes";
    }

    @Override
    public JsonNode collect(UUID userId) {
        return PersonalDataSection.json(jdbc, json, """
                select jsonb_build_object(
                  'preferencias', (select to_jsonb(p) - 'user_id' from notification_preferences p
                                    where p.user_id = :userId),
                  'navegadores', coalesce((select jsonb_agg(jsonb_build_object(
                                                'servico', substring(s.endpoint from 'https://([^/]+)'),
                                                'desde', s.created_at, 'usadoEm', s.last_used_at))
                                             from push_subscriptions s where s.user_id = :userId), '[]'))
                """, userId);
    }
}
