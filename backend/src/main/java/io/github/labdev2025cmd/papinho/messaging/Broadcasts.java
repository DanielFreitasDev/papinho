package io.github.labdev2025cmd.papinho.messaging;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Dados e destinatários das listas de transmissão (GR-03, GR-04). */
@Component
class Broadcasts {

    private final NamedParameterJdbcTemplate jdbc;

    Broadcasts(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void save(UUID id, String name, @Nullable Instant expiresAt, @Nullable Integer messageTtlSeconds, Instant now) {
        jdbc.update("insert into broadcast_details (conversation_id, name, expires_at, message_ttl_seconds, updated_at) "
                + "values (:id, :name, :expiresAt, :ttl, :now) on conflict (conversation_id) do update set "
                + "name = excluded.name, expires_at = excluded.expires_at, "
                + "message_ttl_seconds = excluded.message_ttl_seconds, updated_at = excluded.updated_at",
                new MapSqlParameterSource("id", id).addValue("name", name)
                        .addValue("expiresAt", expiresAt == null ? null : expiresAt.atOffset(ZoneOffset.UTC))
                        .addValue("ttl", messageTtlSeconds).addValue("now", now.atOffset(ZoneOffset.UTC)));
    }

    void replaceRecipients(UUID id, Collection<UUID> recipients) {
        MapSqlParameterSource params = new MapSqlParameterSource("id", id);
        jdbc.update("delete from broadcast_recipients where conversation_id = :id", params);
        for (UUID recipient : recipients) {
            jdbc.update("insert into broadcast_recipients (conversation_id, user_id) values (:id, :userId)",
                    new MapSqlParameterSource("id", id).addValue("userId", recipient));
        }
    }

    List<UUID> recipients(UUID id) {
        return jdbc.queryForList("select user_id from broadcast_recipients where conversation_id = :id",
                new MapSqlParameterSource("id", id), UUID.class);
    }

    Map<UUID, Details> details(Collection<UUID> ids) {
        Map<UUID, Details> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        jdbc.query("""
                select b.conversation_id, b.name, b.expires_at, b.message_ttl_seconds,
                       (select count(*) from broadcast_recipients r where r.conversation_id = b.conversation_id) as recipients
                  from broadcast_details b where b.conversation_id in (:ids)
                """, new MapSqlParameterSource("ids", ids), rs -> {
            OffsetDateTime expires = rs.getObject("expires_at", OffsetDateTime.class);
            UUID id = rs.getObject("conversation_id", UUID.class);
            result.put(id, new Details(id, rs.getString("name"), expires == null ? null : expires.toInstant(),
                    rs.getObject("message_ttl_seconds", Integer.class), rs.getLong("recipients")));
        });
        return result;
    }

    record Details(UUID id, String name, @Nullable Instant expiresAt, @Nullable Integer messageTtlSeconds,
            long recipientCount) {

        boolean expired(Instant now) {
            return expiresAt != null && !expiresAt.isAfter(now);
        }
    }
}
