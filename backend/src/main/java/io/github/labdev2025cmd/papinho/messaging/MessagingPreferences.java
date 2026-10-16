package io.github.labdev2025cmd.papinho.messaging;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Preferências de mensagens de cada pessoa: o timer padrão de mensagens temporárias (MS-07) e "manter arquivadas"
 * (GR-05).
 */
@Component
class MessagingPreferences {

    /** Valores aceitos do timer: 24 horas, 7 dias e 90 dias (nulo = desligado). */
    static final Set<Integer> TIMERS = Set.of(86_400, 604_800, 7_776_000);

    private final NamedParameterJdbcTemplate jdbc;

    MessagingPreferences(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Nullable Integer defaultDisappearing(UUID userId) {
        List<Integer> found = jdbc.query(
                "select default_disappearing_seconds from messaging_preferences where user_id = :userId",
                new MapSqlParameterSource("userId", userId), (rs, index) -> {
                    int value = rs.getInt(1);
                    return rs.wasNull() ? null : value;
                });
        return found.isEmpty() ? null : found.getFirst();
    }

    void changeDefaultDisappearing(UUID userId, @Nullable Integer seconds, Instant now) {
        jdbc.update("""
                insert into messaging_preferences (user_id, default_disappearing_seconds, updated_at)
                values (:userId, :seconds, :now)
                on conflict (user_id) do update
                   set default_disappearing_seconds = excluded.default_disappearing_seconds, updated_at = excluded.updated_at
                """, new MapSqlParameterSource("userId", userId).addValue("seconds", seconds)
                .addValue("now", now.atOffset(ZoneOffset.UTC)));
    }

    boolean keepArchived(UUID userId) {
        List<Boolean> found = jdbc.queryForList("select keep_archived from messaging_preferences where user_id = :userId",
                new MapSqlParameterSource("userId", userId), Boolean.class);
        return !found.isEmpty() && Boolean.TRUE.equals(found.getFirst());
    }

    /** "Manter arquivadas ao receber nova mensagem" (GR-05). */
    void changeKeepArchived(UUID userId, boolean keep, Instant now) {
        jdbc.update("""
                insert into messaging_preferences (user_id, keep_archived, updated_at) values (:userId, :keep, :now)
                on conflict (user_id) do update set keep_archived = excluded.keep_archived, updated_at = excluded.updated_at
                """, new MapSqlParameterSource("userId", userId).addValue("keep", keep)
                .addValue("now", now.atOffset(ZoneOffset.UTC)));
    }
}
