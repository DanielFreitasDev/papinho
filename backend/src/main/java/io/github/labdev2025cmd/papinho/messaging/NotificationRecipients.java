package io.github.labdev2025cmd.papinho.messaging;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.labdev2025cmd.papinho.identity.UserDirectory;
import io.github.labdev2025cmd.papinho.privacy.ConversationLocks;

/**
 * Quem deve saber de uma mensagem nova e com que texto (NT-01, NT-03): os outros participantes ativos (nunca
 * solicitações de quem não é contato, CT-02), com o silêncio da conversa (GR-05), as menções e o conteúdo já sem
 * texto quando a conversa é trancada para aquela pessoa (PV-03) ou a mensagem é de visualização única (MS-08).
 * As preferências de notificação e o "ocultar conteúdo" (PV-08) ficam com o módulo de notificações.
 */
@Component
public class NotificationRecipients {

    /** O mesmo padrão do cliente para {@code @usuario} no texto. */
    private static final Pattern MENTION = Pattern.compile("(^|[^\\w.@])@([a-z0-9._]{3,32})",
            Pattern.CASE_INSENSITIVE);

    private static final int PREVIEW = 120;
    private static final Locale LOCALE = Locale.forLanguageTag("pt-BR");

    private final MessageRepository messages;
    private final ConversationService conversations;
    private final GroupDetailsRepository groups;
    private final UserDirectory users;
    private final ConversationLocks locks;
    private final NamedParameterJdbcTemplate jdbc;
    private final MessageSource texts;
    private final Clock clock;

    NotificationRecipients(MessageRepository messages, ConversationService conversations,
            GroupDetailsRepository groups, UserDirectory users, ConversationLocks locks,
            NamedParameterJdbcTemplate jdbc, MessageSource texts, Clock clock) {
        this.messages = messages;
        this.conversations = conversations;
        this.groups = groups;
        this.users = users;
        this.locks = locks;
        this.jdbc = jdbc;
        this.texts = texts;
        this.clock = clock;
    }

    /** Destinatários da mensagem; vazio se ela já não existe, foi apagada ou é aviso do sistema. */
    @Transactional(readOnly = true)
    public List<Recipient> of(UUID messageId) {
        Message message = messages.findById(messageId).orElse(null);
        if (message == null || message.deleted() || message.system() || message.senderId() == null) {
            return List.of();
        }
        UUID senderId = message.senderId();
        Conversation conversation = conversations.find(message.conversationId());
        boolean group = conversation.kind() == ConversationKind.GROUP;
        String groupName = group ? groups.findById(conversation.id()).map(GroupDetails::name).orElse("") : null;
        Set<String> mentions = mentions(message.body());
        Instant now = clock.instant();
        List<Recipient> result = new ArrayList<>();
        for (MemberRow member : members(conversation.id(), senderId)) {
            String username = users.findById(member.userId()).map(UserDirectory.UserSummary::username).orElse("");
            boolean mentioned = mentions.contains(username) || group && mentions.contains("todos");
            boolean muted = member.mutedUntil() != null && member.mutedUntil().isAfter(now);
            boolean hidden = message.viewOnce() || locks.lockedConversations(member.userId())
                    .contains(conversation.id());
            String sender = conversations.labels(member.userId(), Set.of(senderId)).getOrDefault(senderId, "");
            result.add(new Recipient(member.userId(), conversation.id(), group, groupName, sender,
                    hidden ? "" : preview(message), mentioned, muted, member.muteMentions(), message.silent(),
                    hidden));
        }
        return result;
    }

    /** Não lidas de todas as conversas ativas, para o contador no ícone (NT-03). */
    @Transactional(readOnly = true)
    public long unreadTotal(UUID userId) {
        Long total = jdbc.queryForObject("""
                select count(*) from conversation_members me
                  join messages m on m.conversation_id = me.conversation_id and m.seq > me.read_seq
                 where me.user_id = :userId and me.state = 'ACTIVE' and m.kind <> 'SYSTEM'
                   and m.sender_id is distinct from me.user_id and m.deleted_at is null
                """, new MapSqlParameterSource("userId", userId), Long.class);
        return total == null ? 0 : total;
    }

    private List<MemberRow> members(UUID conversationId, UUID senderId) {
        return jdbc.query("""
                select user_id, muted_until, mute_mentions from conversation_members
                 where conversation_id = :conversationId and user_id <> :senderId and state = 'ACTIVE'
                """, new MapSqlParameterSource("conversationId", conversationId).addValue("senderId", senderId),
                (rs, index) -> {
                    OffsetDateTime until = rs.getObject("muted_until", OffsetDateTime.class);
                    return new MemberRow(rs.getObject("user_id", UUID.class),
                            until == null ? null : until.toInstant(), rs.getBoolean("mute_mentions"));
                });
    }

    private String preview(Message message) {
        String body = message.body().strip();
        String text = body.length() > PREVIEW ? body.substring(0, PREVIEW) + "…" : body;
        if (message.kind() == MessageKind.POLL) {
            return "📊 " + text;
        }
        if (message.stickerId() != null) {
            return texts.getMessage("notification.sticker", null, LOCALE);
        }
        String kind = message.attachmentKind();
        if (kind != null) {
            String label = texts.getMessage("notification.attachment." + kind, null, kind, LOCALE);
            return text.isEmpty() ? label : label + " · " + text;
        }
        return text;
    }

    static Set<String> mentions(String body) {
        Set<String> found = new java.util.HashSet<>();
        Matcher matcher = MENTION.matcher(body);
        while (matcher.find()) {
            String username = matcher.group(2).replaceAll("\\.+$", "").toLowerCase(Locale.ROOT);
            if (username.length() >= 3) {
                found.add(username);
            }
        }
        return found;
    }

    private record MemberRow(UUID userId, @Nullable Instant mutedUntil, boolean muteMentions) {
    }

    /**
     * Uma pessoa a avisar.
     *
     * @param groupName nome do grupo; nulo na conversa individual
     * @param senderLabel quem mandou, como esta pessoa o vê (apelido, nome ou {@code @usuario})
     * @param preview texto curto da mensagem; vazio quando {@code hidden}
     * @param muted a conversa está silenciada por esta pessoa (GR-05)
     * @param muteMentions mesmo silenciada, menções a ela notificam
     * @param silent enviada como silenciosa (MS-11): não notifica
     * @param hidden sem conteúdo: conversa trancada para ela (PV-03) ou visualização única
     */
    public record Recipient(UUID userId, UUID conversationId, boolean group, @Nullable String groupName,
            String senderLabel, String preview, boolean mentioned, boolean muted, boolean muteMentions,
            boolean silent, boolean hidden) {
    }
}
