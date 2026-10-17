package io.github.labdev2025cmd.papinho.messaging;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import io.github.labdev2025cmd.papinho.privacy.ConversationLocks;
import io.github.labdev2025cmd.papinho.shared.personal.PersonalDataSection;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Mensagens que a pessoa escreveu (texto, quando e onde; apagadas para todos ficam sem texto), conversas e como
 * ela as organizou, rascunhos, agendadas, favoritas, reações e preferências (BK-02). Mensagens dos outros ficam de
 * fora: são dados deles (para elas, há a exportação de cada conversa, BK-01).
 */
@Component
class MessagingPersonalData implements PersonalDataSection {

    private final NamedParameterJdbcTemplate jdbc;
    private final JsonMapper json;
    private final ConversationLocks locks;

    MessagingPersonalData(NamedParameterJdbcTemplate jdbc, JsonMapper json, ConversationLocks locks) {
        this.jdbc = jdbc;
        this.json = json;
        this.locks = locks;
    }

    @Override
    public String name() {
        return "mensagens";
    }

    @Override
    public JsonNode collect(UUID userId) {
        // Conversa trancada que o token da requisição não abre: fica só que ela existe (PV-03, ADR 0013).
        Set<UUID> hidden = locks.hiddenConversations(userId);
        String value = jdbc.queryForObject("""
                select jsonb_build_object(
                  'preferencias', (select to_jsonb(p) - 'user_id' from messaging_preferences p
                                    where p.user_id = :userId),
                  'conversas', coalesce((select jsonb_agg(jsonb_build_object(
                                            'id', c.id, 'tipo', c.kind,
                                            'trancada', c.id in (:hidden),
                                            'grupo', (select g.name from group_details g where g.conversation_id = c.id
                                                         and c.id not in (:hidden)),
                                            'com', (select u.username from conversation_members o
                                                      join user_accounts u on u.id = o.user_id
                                                     where c.kind = 'DIRECT' and o.conversation_id = c.id
                                                       and o.user_id <> :userId and c.id not in (:hidden) limit 1),
                                            'situacao', m.state, 'entrouEm', m.joined_at,
                                            'arquivada', m.archived_seq is not null, 'fixadaEm', m.pinned_at,
                                            'silenciadaAte', m.muted_until, 'favorita', m.favorite))
                                           from conversation_members m join conversations c on c.id = m.conversation_id
                                          where m.user_id = :userId), '[]'),
                  'listas', coalesce((select jsonb_agg(jsonb_build_object('nome', f.name,
                                         'conversas', (select coalesce(jsonb_agg(i.conversation_id), '[]')
                                                         from chat_folder_items i where i.folder_id = f.id)))
                                        from chat_folders f where f.user_id = :userId), '[]'),
                  'enviadas', coalesce((select jsonb_agg(jsonb_build_object(
                                           'conversa', x.conversation_id, 'em', x.created_at,
                                           'texto', case when x.deleted_at is null
                                                          and x.conversation_id not in (:hidden) then x.body end,
                                           'anexo', x.attachment_kind, 'editadaEm', x.edited_at,
                                           'apagadaEm', x.deleted_at) order by x.created_at)
                                          from messages x where x.sender_id = :userId and x.kind <> 'SYSTEM'), '[]'),
                  'rascunhos', coalesce((select jsonb_agg(jsonb_build_object('conversa', d.conversation_id,
                                                                            'texto', d.body, 'em', d.updated_at))
                                           from message_drafts d where d.user_id = :userId
                                            and d.conversation_id not in (:hidden)), '[]'),
                  'agendadas', coalesce((select jsonb_agg(jsonb_build_object('conversa', s.conversation_id,
                                                                            'texto', s.body, 'para', s.send_at))
                                           from scheduled_messages s where s.sender_id = :userId
                                            and s.conversation_id not in (:hidden)), '[]'),
                  'favoritas', coalesce((select jsonb_agg(jsonb_build_object('mensagem', s.message_id,
                                                                            'em', s.created_at))
                                           from message_stars s where s.user_id = :userId), '[]'),
                  'reacoes', coalesce((select jsonb_agg(jsonb_build_object('mensagem', r.message_id,
                                                                          'emoji', r.emoji, 'em', r.created_at))
                                         from message_reactions r where r.user_id = :userId), '[]'))
                """, new MapSqlParameterSource("userId", userId).addValue("hidden",
                hidden.isEmpty() ? List.of(new UUID(0, 0)) : hidden), String.class);
        return json.readTree(value == null ? "null" : value);
    }
}
