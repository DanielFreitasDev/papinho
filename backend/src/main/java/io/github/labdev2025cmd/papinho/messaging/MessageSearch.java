package io.github.labdev2025cmd.papinho.messaging;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Consultas de busca de mensagens (GR-06) no PostgreSQL: texto completo com a configuração
 * {@code papinho_portuguese} (português sem acentos, com os plurais em -ões e -ns trocados pelo singular em
 * {@code papinho_search_text}) ou trecho por trigramas ({@code papinho_unaccent}). Só
 * devolve ids; quem chama checa o acesso e monta as mensagens.
 */
@Component
class MessageSearch {

    /** Mensagens que entram na busca de quem pergunta: sem avisos, apagadas, visualização única ou vencidas. */
    private static final String SEARCHABLE = """
              m.kind <> 'SYSTEM' and m.deleted_at is null and not m.view_once
              and (m.expires_at is null or m.expires_at > :now)
              and not exists (select 1 from message_hidden h where h.message_id = m.id and h.user_id = :userId)
            """;

    private static final String MATCHES = """
              (to_tsvector('papinho_portuguese', papinho_search_text(m.body))
                   @@ websearch_to_tsquery('papinho_portuguese', papinho_search_text(:query))
               or papinho_unaccent(m.body) like papinho_unaccent(:pattern) escape '\\')
            """;

    private final NamedParameterJdbcTemplate jdbc;

    MessageSearch(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Busca global: mensagens das conversas visíveis de {@code userId}, menos as de {@code excluded}. */
    List<UUID> global(UUID userId, String query, Collection<UUID> excluded, Instant now, int limit) {
        String sql = "select m.id from messages m"
                + " join conversation_members cm on cm.conversation_id = m.conversation_id"
                + "  and cm.user_id = :userId and cm.state in ('ACTIVE', 'REQUEST')"
                + " where " + SEARCHABLE + " and " + MATCHES
                + (excluded.isEmpty() ? "" : " and m.conversation_id not in (:excluded)")
                + " order by m.created_at desc, m.id limit :limit";
        return jdbc.queryForList(sql, text(userId, query, now).addValue("excluded", excluded)
                .addValue("limit", limit), UUID.class);
    }

    /**
     * Busca dentro de uma conversa, com filtros opcionais de texto, remetente, período e tipo, do mais novo para o
     * mais antigo a partir de {@code beforeSeq}.
     */
    List<UUID> inConversation(UUID userId, UUID conversationId, Filters filters, Instant now, long beforeSeq,
            int limit) {
        StringBuilder sql = new StringBuilder("select m.id from messages m where m.conversation_id = :conversationId")
                .append(" and m.seq < :beforeSeq and ").append(SEARCHABLE);
        String query = filters.query();
        MapSqlParameterSource params = text(userId, query == null ? "" : query, now)
                .addValue("conversationId", conversationId).addValue("beforeSeq", beforeSeq)
                .addValue("limit", limit);
        if (query != null) {
            sql.append(" and ").append(MATCHES);
        }
        if (filters.senderId() != null) {
            sql.append(" and m.sender_id = :senderId");
            params.addValue("senderId", filters.senderId());
        }
        if (filters.from() != null) {
            sql.append(" and m.created_at >= :from");
            params.addValue("from", filters.from().atOffset(ZoneOffset.UTC));
        }
        if (filters.to() != null) {
            sql.append(" and m.created_at < :to");
            params.addValue("to", filters.to().atOffset(ZoneOffset.UTC));
        }
        MessageService.GalleryTab type = filters.type();
        if (type != null) {
            sql.append(switch (type) {
                case MEDIA -> " and m.attachment_kind in ('IMAGE', 'VIDEO')";
                case DOCUMENTS -> " and m.attachment_kind = 'DOCUMENT'";
                case AUDIO -> " and m.attachment_kind = 'AUDIO'";
                case LINKS -> " and m.body ~* 'https?://[^[:space:]]+'";
            });
        }
        sql.append(" order by m.seq desc limit :limit");
        return jdbc.queryForList(sql.toString(), params, UUID.class);
    }

    private static MapSqlParameterSource text(UUID userId, String query, Instant now) {
        String escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        // O padrão passa pela mesma função do índice (minúsculas, sem acentos) dentro da consulta.
        return new MapSqlParameterSource("userId", userId).addValue("query", query)
                .addValue("pattern", "%" + escaped + "%").addValue("now", now.atOffset(ZoneOffset.UTC));
    }

    /**
     * Filtros da busca dentro da conversa.
     *
     * @param query texto (nulo = qualquer)
     * @param from desde (inclusive)
     * @param to até (exclusive)
     * @param type só mídia, links, documentos ou áudios
     */
    record Filters(@Nullable String query, @Nullable UUID senderId, @Nullable Instant from, @Nullable Instant to,
            MessageService.@Nullable GalleryTab type) {
    }
}
