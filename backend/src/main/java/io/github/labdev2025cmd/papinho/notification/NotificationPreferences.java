package io.github.labdev2025cmd.papinho.notification;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Preferências de notificação de cada pessoa (NT-03); tudo ligado por padrão, como no WhatsApp. */
@Service
public class NotificationPreferences {

    static final Preferences DEFAULTS = new Preferences(true, true, true, true, true);

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    NotificationPreferences(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Preferences of(UUID userId) {
        List<Preferences> found = jdbc.query("""
                select enabled, direct, groups, mentions, sound from notification_preferences where user_id = :userId
                """, new MapSqlParameterSource("userId", userId), (rs, index) -> new Preferences(
                rs.getBoolean("enabled"), rs.getBoolean("direct"), rs.getBoolean("groups"),
                rs.getBoolean("mentions"), rs.getBoolean("sound")));
        return found.isEmpty() ? DEFAULTS : found.getFirst();
    }

    @Transactional
    public Preferences change(UUID userId, Preferences next) {
        jdbc.update("""
                insert into notification_preferences (user_id, enabled, direct, groups, mentions, sound, updated_at)
                values (:userId, :enabled, :direct, :groups, :mentions, :sound, :now)
                on conflict (user_id) do update set enabled = excluded.enabled, direct = excluded.direct,
                    groups = excluded.groups, mentions = excluded.mentions, sound = excluded.sound,
                    updated_at = excluded.updated_at
                """, new MapSqlParameterSource("userId", userId).addValue("enabled", next.enabled())
                .addValue("direct", next.direct()).addValue("groups", next.groups())
                .addValue("mentions", next.mentions()).addValue("sound", next.sound())
                .addValue("now", clock.instant().atOffset(ZoneOffset.UTC)));
        return next;
    }

    /**
     * @param enabled notificações ligadas (desligado, nada avisa)
     * @param direct conversas individuais
     * @param groups todas as mensagens de grupo
     * @param mentions menções a mim nos grupos, mesmo com {@code groups} desligado
     * @param sound tocar som (o navegador decide o som; desligado, a notificação é silenciosa)
     */
    public record Preferences(boolean enabled, boolean direct, boolean groups, boolean mentions, boolean sound) {
    }
}
