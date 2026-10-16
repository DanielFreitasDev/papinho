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
 * Lista de conversas de um usuário numa consulta só: participante, a outra pessoa (conversa individual) ou o
 * resumo dos membros (grupo), última mensagem e contagem de não lidas (MS-14). Num grupo a mensagem conta como
 * entregue ou lida quando todos os membros ativos a receberam ou leram.
 */
@Component
class ConversationQueries {

    private static final String LIST = """
            select c.id, c.kind, c.last_seq, c.updated_at, c.created_by, c.disappearing_seconds,
                   me.state, me.role, me.read_seq, me.marked_unread, me.read_receipts as my_read_receipts,
                   peer.user_id as peer_id, peer.state as peer_state, peer.read_receipts as peer_read_receipts,
                   coalesce(grp.sharing, 0) as group_sharing,
                   case when c.kind = 'GROUP' then grp.delivered else peer.delivered_seq end as peer_delivered_seq,
                   case when c.kind = 'GROUP' then grp.read else peer.read_seq end as peer_read_seq,
                   coalesce(grp.members, 0) + 1 as member_count,
                   (select count(*) from messages m where m.conversation_id = c.id and m.seq > me.read_seq
                       and m.kind <> 'SYSTEM' and m.sender_id is distinct from me.user_id) as unread_count,
                   (select min(m.seq) from messages m where m.conversation_id = c.id and m.seq > me.read_seq
                       and m.kind <> 'SYSTEM' and m.sender_id is distinct from me.user_id) as first_unread_seq,
                   last.id as last_id, last.seq as last_seq_of_message, last.sender_id as last_sender_id,
                   left(last.body, 200) as last_body, last.created_at as last_created_at,
                   last.attachment_kind as last_attachment_kind, last.deleted_at is not null as last_deleted,
                   last.kind as last_kind, last.sticker_id is not null as last_sticker, draft.body as draft,
                   -- GR-05: arquivada até chegar mensagem nova de outra pessoa (ou sempre, com "manter arquivadas").
                   me.archived_seq is not null
                       and (coalesce(prefs.keep_archived, false)
                            or not exists (select 1 from messages m where m.conversation_id = c.id
                                              and m.seq > me.archived_seq and m.kind <> 'SYSTEM'
                                              and m.sender_id is distinct from me.user_id)) as archived,
                   me.pinned_at, me.muted_until, me.mute_mentions, me.favorite,
                   array(select fi.folder_id from chat_folder_items fi
                           join chat_folders f on f.id = fi.folder_id
                          where fi.conversation_id = c.id and f.user_id = me.user_id) as folder_ids
              from conversation_members me
              join conversations c on c.id = me.conversation_id
              left join message_drafts draft on draft.user_id = me.user_id and draft.conversation_id = c.id
              left join messaging_preferences prefs on prefs.user_id = me.user_id
              left join conversation_members peer
                     on c.kind = 'DIRECT' and peer.conversation_id = c.id and peer.user_id <> me.user_id
              left join lateral (select count(*) as members, coalesce(min(o.delivered_seq), 0) as delivered,
                                        -- PV-06: quem desligou a confirmação de leitura no grupo fica de fora.
                                        coalesce(min(o.read_seq) filter (where o.read_receipts is not false), 0)
                                            as read,
                                        count(*) filter (where o.read_receipts is not false) as sharing
                                   from conversation_members o
                                  where c.kind = 'GROUP' and o.conversation_id = c.id and o.user_id <> me.user_id
                                    and o.state = 'ACTIVE') grp on true
              left join lateral (select m.id, m.seq, m.sender_id, m.body, m.created_at, m.attachment_kind, m.deleted_at,
                                        m.kind, m.sticker_id
                                   from messages m
                                  where m.conversation_id = c.id
                                    and not exists (select 1 from message_hidden h
                                                     where h.message_id = m.id and h.user_id = me.user_id)
                                  order by m.seq desc limit 1) last on true
             where me.user_id = :userId and me.state in (:states)
               and (:conversationId::uuid is null
                    and (c.last_seq > 0 or c.created_by = me.user_id or draft.body is not null)
                    -- "Apagar conversa" (GR-05): fora da lista até chegar mensagem nova.
                    and (me.removed_seq is null or c.last_seq > me.removed_seq)
                    or c.id = :conversationId::uuid)
             order by me.pinned_at desc nulls last, c.updated_at desc
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
                        instant(rs.getObject("last_created_at", OffsetDateTime.class)),
                        rs.getString("last_attachment_kind"), rs.getBoolean("last_deleted"),
                        MessageKind.valueOf(rs.getString("last_kind")), rs.getBoolean("last_sticker"));
        long firstUnread = rs.getLong("first_unread_seq");
        return new Row(rs.getObject("id", UUID.class), ConversationKind.valueOf(rs.getString("kind")),
                rs.getLong("last_seq"), instant(rs.getObject("updated_at", OffsetDateTime.class)),
                rs.getObject("created_by", UUID.class), rs.getObject("disappearing_seconds", Integer.class),
                MemberState.valueOf(rs.getString("state")),
                MemberRole.valueOf(rs.getString("role")), rs.getLong("member_count"),
                rs.getLong("read_seq"), rs.getBoolean("marked_unread"), rs.getObject("peer_id", UUID.class),
                rs.getString("peer_state") == null ? null : MemberState.valueOf(rs.getString("peer_state")),
                rs.getLong("peer_delivered_seq"), rs.getLong("peer_read_seq"), rs.getLong("unread_count"),
                firstUnread == 0 ? null : firstUnread, last, rs.getString("draft"),
                rs.getObject("my_read_receipts", Boolean.class), rs.getObject("peer_read_receipts", Boolean.class),
                rs.getLong("group_sharing"), toOrganization(rs));
    }

    private static ConversationService.Organization toOrganization(ResultSet rs) throws SQLException {
        OffsetDateTime pinnedAt = rs.getObject("pinned_at", OffsetDateTime.class);
        OffsetDateTime mutedUntil = rs.getObject("muted_until", OffsetDateTime.class);
        java.sql.Array folders = rs.getArray("folder_ids");
        List<UUID> folderIds = folders == null ? List.of() : List.of((UUID[]) folders.getArray());
        return new ConversationService.Organization(rs.getBoolean("archived"), pinnedAt == null ? null : pinnedAt.toInstant(),
                mutedUntil == null ? null : mutedUntil.toInstant(), rs.getBoolean("mute_mentions"),
                rs.getBoolean("favorite"), folderIds);
    }

    private static Instant instant(@Nullable OffsetDateTime timestamp) {
        return timestamp == null ? Instant.EPOCH : timestamp.toInstant();
    }

    /** Linha crua da lista; o serviço aplica as regras de privacidade antes de expor. */
    record Row(UUID id, ConversationKind kind, long lastSeq, Instant updatedAt, @Nullable UUID createdBy,
            @Nullable Integer disappearingSeconds, MemberState state, MemberRole role, long memberCount, long readSeq, boolean markedUnread,
            @Nullable UUID peerId,
            @Nullable MemberState peerState, long peerDeliveredSeq, long peerReadSeq, long unreadCount,
            @Nullable Long firstUnreadSeq, @Nullable LastMessage last, @Nullable String draft,
            @Nullable Boolean myReadReceipts, @Nullable Boolean peerReadReceipts, long groupSharing,
            ConversationService.Organization organization) {
    }

    record LastMessage(UUID id, long seq, @Nullable UUID senderId, String body, Instant createdAt,
            @Nullable String attachmentKind, boolean deleted, MessageKind kind, boolean sticker) {
    }
}
