package io.github.labdev2025cmd.papinho.messaging;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Organização da lista de conversas de cada pessoa (GR-05): arquivar, fixar (até {@value #MAX_PINNED}), silenciar,
 * favoritar, listas personalizadas (pastas), limpar e apagar conversa. Tudo vale só para quem pediu; os outros
 * participantes não ficam sabendo.
 */
@Service
public class ChatListService {

    /** Conversas fixadas no topo da lista, como no WhatsApp. */
    static final int MAX_PINNED = 3;

    /** Pastas por pessoa. */
    static final int MAX_FOLDERS = 20;

    /** "Silenciar sempre" guarda esta data. */
    static final Instant MUTED_FOREVER = OffsetDateTime.of(9999, 12, 31, 0, 0, 0, 0, ZoneOffset.UTC).toInstant();

    private final ConversationService conversations;
    private final SequenceAllocator sequences;
    private final NamedParameterJdbcTemplate jdbc;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    ChatListService(ConversationService conversations, SequenceAllocator sequences, NamedParameterJdbcTemplate jdbc,
            ApplicationEventPublisher events, Clock clock) {
        this.conversations = conversations;
        this.sequences = sequences;
        this.jdbc = jdbc;
        this.events = events;
        this.clock = clock;
    }

    /** Arquiva até o seq atual; arquivar também solta a conversa do topo (como no WhatsApp). */
    @Transactional
    public void archive(UUID me, UUID conversationId, boolean archived) {
        conversations.requireMember(me, conversationId);
        update(me, conversationId, archived
                ? "archived_seq = (select last_seq from conversations where id = :conversationId), pinned_at = null"
                : "archived_seq = null");
    }

    /** Fixa no topo; a quarta dá 409. Fixar tira do arquivo. */
    @Transactional
    public void pin(UUID me, UUID conversationId, boolean pinned) {
        ConversationMember member = conversations.requireMember(me, conversationId);
        if (!pinned) {
            update(me, conversationId, "pinned_at = null");
            return;
        }
        // Serializa as fixações da mesma pessoa para o limite valer com dois dispositivos ao mesmo tempo.
        sequences.lockKey("pins:" + me);
        Integer others = jdbc.queryForObject("""
                select count(*) from conversation_members
                 where user_id = :userId and pinned_at is not null and conversation_id <> :conversationId
                   and state in ('ACTIVE', 'REQUEST')
                """, params(me, conversationId), Integer.class);
        if (others != null && others >= MAX_PINNED) {
            throw new MessagingExceptions.PinLimitException();
        }
        update(me, member.conversationId(),
                "pinned_at = coalesce(pinned_at, :now), archived_seq = null, removed_seq = null");
    }

    @Transactional
    public void favorite(UUID me, UUID conversationId, boolean favorite) {
        conversations.requireMember(me, conversationId);
        update(me, conversationId, favorite ? "favorite = true" : "favorite = false");
    }

    /**
     * Silencia por 8 horas, 1 semana ou sempre ({@code duration} nulo tira o silêncio). Com {@code mentions}, as
     * menções a mim ainda notificam.
     */
    @Transactional
    public void mute(UUID me, UUID conversationId, @Nullable MuteDuration duration, boolean mentions) {
        conversations.requireMember(me, conversationId);
        Instant until = duration == null ? null : duration.until(clock.instant());
        jdbc.update("""
                update conversation_members set muted_until = :until, mute_mentions = :mentions
                 where conversation_id = :conversationId and user_id = :userId
                """, params(me, conversationId).addValue("until", until == null ? null : until.atOffset(ZoneOffset.UTC))
                .addValue("mentions", duration != null && mentions));
        notifyMe(me, conversationId);
    }

    /**
     * Limpa a conversa só para mim: as mensagens até agora somem (como "apagar para mim" em todas), menos as
     * favoritas se {@code keepStarred}. Os meus outros dispositivos recebem o aviso para esvaziar a tela.
     */
    @Transactional
    public void clear(UUID me, UUID conversationId, boolean keepStarred) {
        conversations.requireReadable(me, conversationId);
        hideAll(me, conversationId, keepStarred);
        events.publishEvent(new MessagingEvents.ConversationCleared(conversationId, me));
        notifyMe(me, conversationId);
    }

    /**
     * Apaga a conversa da minha lista: limpa tudo, tira das pastas e do topo e só volta a aparecer quando chegar
     * mensagem nova. Num grupo continuo membro (sair é outra ação).
     */
    @Transactional
    public void remove(UUID me, UUID conversationId) {
        conversations.requireReadable(me, conversationId);
        hideAll(me, conversationId, false);
        update(me, conversationId, "removed_seq = (select last_seq from conversations where id = :conversationId), "
                + "pinned_at = null, archived_seq = null, favorite = false");
        jdbc.update("""
                delete from chat_folder_items fi using chat_folders f
                 where f.id = fi.folder_id and f.user_id = :userId and fi.conversation_id = :conversationId
                """, params(me, conversationId));
        events.publishEvent(new MessagingEvents.ConversationCleared(conversationId, me));
    }

    @Transactional(readOnly = true)
    public List<FolderView> folders(UUID me) {
        return jdbc.query("""
                select f.id, f.name, f.position, array(select fi.conversation_id from chat_folder_items fi
                                                          where fi.folder_id = f.id) as conversation_ids
                  from chat_folders f where f.user_id = :userId order by f.position, f.created_at
                """, new MapSqlParameterSource("userId", me), (rs, index) -> new FolderView(
                rs.getObject("id", UUID.class), rs.getString("name"),
                List.of((UUID[]) rs.getArray("conversation_ids").getArray())));
    }

    /** Cria uma pasta com estas conversas (as que não são minhas dão 404). */
    @Transactional
    public FolderView createFolder(UUID me, String name, List<UUID> conversationIds) {
        sequences.lockKey("folders:" + me);
        Integer count = jdbc.queryForObject("select count(*) from chat_folders where user_id = :userId",
                new MapSqlParameterSource("userId", me), Integer.class);
        if (count != null && count >= MAX_FOLDERS) {
            throw new MessagingExceptions.FolderLimitException();
        }
        UUID id = jdbc.queryForObject("""
                insert into chat_folders (id, user_id, name, position, created_at)
                values (uuidv7(), :userId, :name, :position, :now) returning id
                """, new MapSqlParameterSource("userId", me).addValue("name", name.strip())
                .addValue("position", count == null ? 0 : count)
                .addValue("now", clock.instant().atOffset(ZoneOffset.UTC)), UUID.class);
        replaceItems(me, id, conversationIds);
        return requireFolder(me, id);
    }

    /** Renomeia e troca as conversas da pasta. */
    @Transactional
    public FolderView updateFolder(UUID me, UUID folderId, String name, List<UUID> conversationIds) {
        requireFolder(me, folderId);
        jdbc.update("update chat_folders set name = :name where id = :id",
                new MapSqlParameterSource("id", folderId).addValue("name", name.strip()));
        replaceItems(me, folderId, conversationIds);
        return requireFolder(me, folderId);
    }

    @Transactional
    public void deleteFolder(UUID me, UUID folderId) {
        requireFolder(me, folderId);
        jdbc.update("delete from chat_folders where id = :id", new MapSqlParameterSource("id", folderId));
        events.publishEvent(new MessagingEvents.FoldersChanged(me));
    }

    /** Põe ou tira uma conversa de uma pasta (pelo menu da conversa). */
    @Transactional
    public FolderView toggleFolderItem(UUID me, UUID folderId, UUID conversationId, boolean member) {
        FolderView folder = requireFolder(me, folderId);
        conversations.requireMember(me, conversationId);
        Set<UUID> ids = new LinkedHashSet<>(folder.conversationIds());
        if (member) {
            ids.add(conversationId);
        } else {
            ids.remove(conversationId);
        }
        replaceItems(me, folderId, List.copyOf(ids));
        return requireFolder(me, folderId);
    }

    private void replaceItems(UUID me, UUID folderId, List<UUID> conversationIds) {
        Set<UUID> unique = new LinkedHashSet<>(conversationIds);
        unique.forEach(id -> conversations.requireMember(me, id));
        jdbc.update("delete from chat_folder_items where folder_id = :folderId",
                new MapSqlParameterSource("folderId", folderId));
        unique.forEach(id -> jdbc.update(
                "insert into chat_folder_items (folder_id, conversation_id) values (:folderId, :conversationId)",
                new MapSqlParameterSource("folderId", folderId).addValue("conversationId", id)));
        events.publishEvent(new MessagingEvents.FoldersChanged(me));
    }

    private FolderView requireFolder(UUID me, UUID folderId) {
        return folders(me).stream().filter(folder -> folder.id().equals(folderId)).findFirst()
                .orElseThrow(MessagingExceptions.NotFoundException::new);
    }

    private void hideAll(UUID me, UUID conversationId, boolean keepStarred) {
        jdbc.update("""
                insert into message_hidden (user_id, message_id, created_at)
                select :userId, m.id, :now from messages m
                 where m.conversation_id = :conversationId
                   and (not :keepStarred or not exists (select 1 from message_stars s
                                                         where s.message_id = m.id and s.user_id = :userId))
                on conflict do nothing
                """, params(me, conversationId).addValue("keepStarred", keepStarred));
        if (!keepStarred) {
            jdbc.update("""
                    delete from message_stars s using messages m
                     where s.message_id = m.id and s.user_id = :userId and m.conversation_id = :conversationId
                    """, params(me, conversationId));
        }
    }

    private void update(UUID me, UUID conversationId, String assignments) {
        jdbc.update("update conversation_members set " + assignments
                + " where conversation_id = :conversationId and user_id = :userId", params(me, conversationId));
        notifyMe(me, conversationId);
    }

    private MapSqlParameterSource params(UUID me, UUID conversationId) {
        return new MapSqlParameterSource("userId", me).addValue("conversationId", conversationId)
                .addValue("now", clock.instant().atOffset(ZoneOffset.UTC));
    }

    /** Os meus outros dispositivos atualizam a lista. */
    private void notifyMe(UUID me, UUID conversationId) {
        events.publishEvent(new MessagingEvents.ConversationUpdated(conversationId, Set.of(me)));
    }

    /** Por quanto tempo silenciar (GR-05). */
    public enum MuteDuration {
        EIGHT_HOURS, ONE_WEEK, ALWAYS;

        Instant until(Instant now) {
            return switch (this) {
                case EIGHT_HOURS -> now.plus(Duration.ofHours(8));
                case ONE_WEEK -> now.plus(Duration.ofDays(7));
                case ALWAYS -> MUTED_FOREVER;
            };
        }
    }

    /** Lista personalizada (pasta) de conversas. */
    public record FolderView(UUID id, String name, List<UUID> conversationIds) {
    }
}
