package io.github.labdev2025cmd.papinho.messaging;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Mensagens que cada pessoa apagou só para si (MS-06). */
@Component
class HiddenMessages {

    private final NamedParameterJdbcTemplate jdbc;

    HiddenMessages(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void hide(UUID userId, UUID messageId, Instant now) {
        jdbc.update("""
                insert into message_hidden (user_id, message_id, created_at) values (:userId, :messageId, :now)
                on conflict do nothing
                """, new MapSqlParameterSource("userId", userId).addValue("messageId", messageId)
                .addValue("now", now.atOffset(ZoneOffset.UTC)));
    }

    /** Quais destas mensagens {@code userId} escondeu. */
    Set<UUID> hiddenAmong(UUID userId, Collection<UUID> messageIds) {
        if (messageIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.queryForList(
                "select message_id from message_hidden where user_id = :userId and message_id in (:ids)",
                new MapSqlParameterSource("userId", userId).addValue("ids", messageIds), UUID.class));
    }
}
