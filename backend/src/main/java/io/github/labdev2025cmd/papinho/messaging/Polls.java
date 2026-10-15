package io.github.labdev2025cmd.papinho.messaging;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Opções e votos das enquetes (MS-09), gravados ao lado da mensagem. */
@Component
class Polls {

    private final NamedParameterJdbcTemplate jdbc;

    Polls(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void create(UUID messageId, boolean multiple, boolean anonymous, @Nullable Instant closesAt,
            List<String> options) {
        jdbc.update("""
                insert into polls (message_id, multiple, anonymous, closes_at)
                values (:messageId, :multiple, :anonymous, :closesAt)
                """, new MapSqlParameterSource("messageId", messageId).addValue("multiple", multiple)
                .addValue("anonymous", anonymous)
                .addValue("closesAt", closesAt == null ? null : closesAt.atOffset(ZoneOffset.UTC)));
        for (int i = 0; i < options.size(); i++) {
            jdbc.update("""
                    insert into poll_options (id, message_id, position, text)
                    values (:id, :messageId, :position, :text)
                    """, new MapSqlParameterSource("id", UUID.randomUUID()).addValue("messageId", messageId)
                    .addValue("position", i).addValue("text", options.get(i)));
        }
    }

    /** Enquetes destas mensagens, com opções em ordem e os votos de cada uma. */
    Map<UUID, Poll> find(Collection<UUID> messageIds) {
        if (messageIds.isEmpty()) {
            return Map.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource("ids", messageIds);
        Map<UUID, Poll> result = new HashMap<>();
        jdbc.query("select message_id, multiple, anonymous, closes_at from polls where message_id in (:ids)", params,
                rs -> {
                    OffsetDateTime closes = rs.getObject("closes_at", OffsetDateTime.class);
                    UUID id = rs.getObject("message_id", UUID.class);
                    result.put(id, new Poll(id, rs.getBoolean("multiple"), rs.getBoolean("anonymous"),
                            closes == null ? null : closes.toInstant(), new ArrayList<>()));
                });
        Map<UUID, Option> options = new HashMap<>();
        jdbc.query("""
                select id, message_id, text from poll_options where message_id in (:ids) order by message_id, position
                """, params, rs -> {
            Option option = new Option(rs.getObject("id", UUID.class), rs.getString("text"), new ArrayList<>());
            options.put(option.id(), option);
            Poll poll = result.get(rs.getObject("message_id", UUID.class));
            if (poll != null) {
                poll.options().add(option);
            }
        });
        jdbc.query("select option_id, user_id from poll_votes where message_id in (:ids) order by created_at", params,
                rs -> {
                    Option option = options.get(rs.getObject("option_id", UUID.class));
                    if (option != null) {
                        option.voters().add(rs.getObject("user_id", UUID.class));
                    }
                });
        return result;
    }

    /** Troca os votos de {@code userId} pelos de {@code optionIds} (vazio = tira o voto). */
    void vote(UUID messageId, UUID userId, Set<UUID> optionIds, Instant now) {
        MapSqlParameterSource params = new MapSqlParameterSource("messageId", messageId).addValue("userId", userId)
                .addValue("now", now.atOffset(ZoneOffset.UTC));
        jdbc.update("delete from poll_votes where message_id = :messageId and user_id = :userId", params);
        for (UUID optionId : new LinkedHashSet<>(optionIds)) {
            jdbc.update("""
                    insert into poll_votes (option_id, user_id, message_id, created_at)
                    values (:optionId, :userId, :messageId, :now)
                    """, new MapSqlParameterSource(params.getValues()).addValue("optionId", optionId));
        }
    }

    void delete(UUID messageId) {
        jdbc.update("delete from polls where message_id = :messageId", new MapSqlParameterSource("messageId", messageId));
    }

    record Poll(UUID messageId, boolean multiple, boolean anonymous, @Nullable Instant closesAt, List<Option> options) {
    }

    record Option(UUID id, String text, List<UUID> voters) {
    }
}
