package io.github.labdev2025cmd.papinho.messaging;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.labdev2025cmd.papinho.contact.ContactQueries;
import io.github.labdev2025cmd.papinho.identity.UserDirectory;
import io.github.labdev2025cmd.papinho.identity.UserDirectory.UserSummary;
import io.github.labdev2025cmd.papinho.messaging.MessagingExceptions.CannotAddException;
import io.github.labdev2025cmd.papinho.messaging.MessagingExceptions.InvalidGroupException;
import io.github.labdev2025cmd.papinho.messaging.MessagingExceptions.NotFoundException;

/**
 * Listas de transmissão (GR-03): o dono escolhe contatos e envia para todos de uma vez; cada destinatário recebe
 * na conversa individual, e só quem tem o dono nos contatos recebe. Temporárias (GR-04): data de expiração da
 * lista e/ou timer das mensagens enviadas por ela.
 */
@Service
public class BroadcastService {

    /** Destinatários por lista, como no WhatsApp. */
    static final int MAX_RECIPIENTS = 256;

    private final ConversationRepository conversations;
    private final ConversationMemberRepository members;
    private final ConversationService conversationService;
    private final Broadcasts broadcasts;
    private final UserDirectory users;
    private final ContactQueries contacts;
    private final GroupService groups;
    private final Clock clock;

    BroadcastService(ConversationRepository conversations, ConversationMemberRepository members,
            ConversationService conversationService, Broadcasts broadcasts, UserDirectory users,
            ContactQueries contacts, GroupService groups, Clock clock) {
        this.conversations = conversations;
        this.members = members;
        this.conversationService = conversationService;
        this.broadcasts = broadcasts;
        this.users = users;
        this.contacts = contacts;
        this.groups = groups;
        this.clock = clock;
    }

    @Transactional
    public ConversationService.ConversationView create(UUID me, String name, List<UUID> recipientIds,
            @Nullable Instant expiresAt, @Nullable Integer messageTtlSeconds) {
        Set<UUID> recipients = validRecipients(me, recipientIds);
        Instant now = clock.instant();
        validate(name, expiresAt, messageTtlSeconds, now);
        Conversation conversation = conversations.saveAndFlush(Conversation.broadcast(me, now));
        members.saveAndFlush(new ConversationMember(conversation.id(), me, MemberState.ACTIVE, MemberRole.ADMIN, now));
        broadcasts.save(conversation.id(), name.strip(), expiresAt, messageTtlSeconds, now);
        broadcasts.replaceRecipients(conversation.id(), recipients);
        return conversationService.view(me, conversation.id());
    }

    @Transactional
    public ConversationService.ConversationView update(UUID me, UUID id, String name, List<UUID> recipientIds,
            @Nullable Instant expiresAt, @Nullable Integer messageTtlSeconds) {
        requireOwn(me, id);
        Set<UUID> recipients = validRecipients(me, recipientIds);
        Instant now = clock.instant();
        // Vencida, a lista fica só para consulta: mudar a validade a reabriria (GR-04).
        Broadcasts.Details current = broadcasts.details(List.of(id)).get(id);
        if (current == null || current.expired(now)) {
            throw new MessagingExceptions.BroadcastExpiredException();
        }
        validate(name, expiresAt, messageTtlSeconds, now);
        broadcasts.save(id, name.strip(), expiresAt, messageTtlSeconds, now);
        broadcasts.replaceRecipients(id, recipients);
        return conversationService.view(me, id);
    }

    /** Destinatários da lista, como o dono os vê. */
    @Transactional(readOnly = true)
    public List<GroupService.MemberView> recipients(UUID me, UUID id) {
        requireOwn(me, id);
        return groups.describe(me, broadcasts.recipients(id));
    }

    private void requireOwn(UUID me, UUID id) {
        Conversation conversation = conversations.findById(id).filter(c -> c.kind() == ConversationKind.BROADCAST)
                .orElseThrow(NotFoundException::new);
        conversationService.requireMember(me, conversation.id());
    }

    /** Contatos ativos de quem envia, sem bloqueio entre os dois. */
    private Set<UUID> validRecipients(UUID me, List<UUID> recipientIds) {
        Set<UUID> recipients = new LinkedHashSet<>(recipientIds);
        recipients.remove(me);
        if (recipients.isEmpty() || recipients.size() > MAX_RECIPIENTS) {
            throw new InvalidGroupException("recipients");
        }
        for (UUID recipient : recipients) {
            UserSummary user = users.findById(recipient).filter(UserSummary::active)
                    .orElseThrow(CannotAddException::new);
            if (!contacts.isContact(me, user.id()) || contacts.isBlockedEitherWay(me, user.id())) {
                throw new CannotAddException();
            }
        }
        return recipients;
    }

    private static void validate(String name, @Nullable Instant expiresAt, @Nullable Integer ttl, Instant now) {
        String trimmed = name.strip();
        if (trimmed.isEmpty() || trimmed.length() > GroupDetails.MAX_NAME) {
            throw new InvalidGroupException("name");
        }
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            throw new InvalidGroupException("expires in the past");
        }
        if (ttl != null && !MessagingPreferences.TIMERS.contains(ttl)) {
            throw new InvalidGroupException("timer");
        }
    }
}
