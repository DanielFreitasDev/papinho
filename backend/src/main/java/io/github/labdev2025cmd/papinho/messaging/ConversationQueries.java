package io.github.labdev2025cmd.papinho.messaging;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Lista de conversas de um usuário numa consulta só: participante, a outra pessoa, última mensagem e contagem
 * de não lidas (MS-14).
 */
@Component
class ConversationQueries {

    private static final String LIST = """
            select c.id, c.kind, c.last_seq, c.updated_at, c.created_by,
                   me.state, me.read_seq, me.marked_unread,
                   peer.user_id as peer_id, peer.state as peer_state,
                   peer.delivered_seq as peer_delivered_seq, peer.read_seq as peer_read_seq,
                   (select count(*) from messages m where m.conversation_id = c.id and m.seq > me.read_seq
                       and m.sender_id is distinct from me.user_id) as unread_count,
                   (select min(m.seq) from messages m where m.conversation_id = c.id and m.seq > me.read_seq
                       and m.sender_id is distinct from me.user_id) as first_unread_seq,
                   last.id as last_id, last.seq as last_seq_of_message, last.sender_id as last_sender_id,
                   left(last.body, 200) as last_body, last.created_at as last_created_at
              from conversation_members me
              join conversations c on c.id = me.conversation_id
              left join conversation_members peer on peer.conversation_id = c.id and peer.user_id <> me.user_id
              left join lateral (select m.id, m.seq, m.sender_id, m.body, m.created_at from messages m
                                  where m.conversation_id = c.id order by m.seq desc limit 1) last on true
             where me.user_id = :userId and me.state in (:states)
               and (:conversationId::uuid is null and (c.last_seq > 0 or c.created_by = me.user_id)
                    or c.id = :conversationId::uuid)
             order by c.updated_at desc
            """;

    private final NamedParameterJdbcTemplate jdbc;

    ConversationQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<Row> list(UUID userId, Collection<MemberState> states, @Nullable UUID conversationId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("userId", userId)
                .addValue("states", states.stream().map(Enum::name).toList())
                .addValue("conversationId", conversationId == null ? null : conversationId.toString());
        return jdbc.query(LIST, params, (rs, index) -> toRow(rs));
    }

    /** Quem tem conversa individual com {@code userId} em que os dois estão ACTIVE. */
    List<UUID> mutualPeers(UUID userId) {
        return jdbc.queryForList("""
                select peer.user_id from conversation_members me
                  join conversations c on c.id = me.conversation_id and c.kind = 'DIRECT'
                  join conversation_members peer on peer.conversation_id = c.id and peer.user_id <> me.user_id
                 where me.user_id = :userId and me.state = 'ACTIVE' and peer.state = 'ACTIVE'
                """, new MapSqlParameterSource("userId", userId), UUID.class);
    }

    private static Row toRow(ResultSet rs) throws SQLException {
        UUID lastId = rs.getObject("last_id", UUID.class);
        LastMessage last = lastId == null ? null
                : new LastMessage(lastId, rs.getLong("last_seq_of_message"),
                        rs.getObject("last_sender_id", UUID.class), rs.getString("last_body"),
                        instant(rs.getObject("last_created_at", OffsetDateTime.class)));
        long firstUnread = rs.getLong("first_unread_seq");
        return new Row(rs.getObject("id", UUID.class), ConversationKind.valueOf(rs.getString("kind")),
                rs.getLong("last_seq"), instant(rs.getObject("updated_at", OffsetDateTime.class)),
                rs.getObject("created_by", UUID.class), MemberState.valueOf(rs.getString("state")),
                rs.getLong("read_seq"), rs.getBoolean("marked_unread"), rs.getObject("peer_id", UUID.class),
                rs.getString("peer_state") == null ? null : MemberState.valueOf(rs.getString("peer_state")),
                rs.getLong("peer_delivered_seq"), rs.getLong("peer_read_seq"), rs.getLong("unread_count"),
                firstUnread == 0 ? null : firstUnread, last);
    }

    private static Instant instant(@Nullable OffsetDateTime timestamp) {
        return timestamp == null ? Instant.EPOCH : timestamp.toInstant();
    }

    /** Linha crua da lista; o serviço aplica as regras de privacidade antes de expor. */
    record Row(UUID id, ConversationKind kind, long lastSeq, Instant updatedAt, @Nullable UUID createdBy,
            MemberState state, long readSeq, boolean markedUnread, @Nullable UUID peerId,
            @Nullable MemberState peerState, long peerDeliveredSeq, long peerReadSeq, long unreadCount,
            @Nullable Long firstUnreadSeq, @Nullable LastMessage last) {
    }

    record LastMessage(UUID id, long seq, @Nullable UUID senderId, String body, Instant createdAt) {
    }
}
