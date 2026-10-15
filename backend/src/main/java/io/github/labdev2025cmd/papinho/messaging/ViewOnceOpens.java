package io.github.labdev2025cmd.papinho.messaging;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Quem já abriu cada mensagem de visualização única e o token emitido para isso (MS-08). */
@Component
class ViewOnceOpens {

    private final NamedParameterJdbcTemplate jdbc;

    ViewOnceOpens(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Registra a abertura com o hash do token; falso quando a pessoa já tinha aberto (o token não é reemitido). */
    boolean open(UUID messageId, UUID userId, String tokenHash, Instant now, Instant expiresAt) {
        return jdbc.update("""
                insert into view_once_opens (message_id, user_id, token_hash, opened_at, token_expires_at)
                values (:messageId, :userId, :tokenHash, :now, :expiresAt) on conflict do nothing
                """, new MapSqlParameterSource("messageId", messageId).addValue("userId", userId)
                .addValue("tokenHash", tokenHash).addValue("now", now.atOffset(ZoneOffset.UTC))
                .addValue("expiresAt", expiresAt.atOffset(ZoneOffset.UTC))) == 1;
    }

    /** Mensagem de um token ainda válido emitido para {@code userId}. */
    Optional<UUID> messageOfToken(String tokenHash, UUID userId, Instant now) {
        List<UUID> found = jdbc.queryForList("""
                select message_id from view_once_opens
                 where token_hash = :tokenHash and user_id = :userId and token_expires_at > :now
                """, new MapSqlParameterSource("tokenHash", tokenHash).addValue("userId", userId)
                .addValue("now", now.atOffset(ZoneOffset.UTC)), UUID.class);
        return found.stream().findFirst();
    }

    /** Quais destas mensagens {@code userId} já abriu. */
    Set<UUID> openedBy(UUID userId, Collection<UUID> messageIds) {
        if (messageIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.queryForList(
                "select message_id from view_once_opens where user_id = :userId and message_id in (:ids)",
                new MapSqlParameterSource("userId", userId).addValue("ids", messageIds), UUID.class));
    }

    /** Quantas pessoas abriram cada mensagem. */
    Map<UUID, Long> counts(Collection<UUID> messageIds) {
        Map<UUID, Long> result = new HashMap<>();
        if (messageIds.isEmpty()) {
            return result;
        }
        jdbc.query("select message_id, count(*) from view_once_opens where message_id in (:ids) group by message_id",
                new MapSqlParameterSource("ids", messageIds),
                rs -> {
                    result.put(rs.getObject(1, UUID.class), rs.getLong(2));
                });
        return result;
    }

    /**
     * Mensagens cujo arquivo deve sair do servidor: passou o prazo, ou todos os destinatários ativos abriram e os
     * tokens já venceram.
     */
    List<UUID> purgeable(Instant now, Instant cutoff, int limit) {
        return jdbc.queryForList("""
                select m.id from messages m
                 where m.view_once and m.attachment_id is not null
                   and (m.created_at < :cutoff
                        or (not exists (select 1 from conversation_members cm
                                         where cm.conversation_id = m.conversation_id and cm.state = 'ACTIVE'
                                           and cm.user_id is distinct from m.sender_id
                                           and not exists (select 1 from view_once_opens o
                                                            where o.message_id = m.id and o.user_id = cm.user_id))
                            and not exists (select 1 from view_once_opens o
                                             where o.message_id = m.id and o.token_expires_at > :now)))
                 order by m.created_at limit :limit
                """, new MapSqlParameterSource("now", now.atOffset(ZoneOffset.UTC))
                .addValue("cutoff", cutoff.atOffset(ZoneOffset.UTC)).addValue("limit", limit), UUID.class);
    }
}
