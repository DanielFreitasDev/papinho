package io.github.labdev2025cmd.papinho.messaging;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.labdev2025cmd.papinho.admin.InstanceSettingsService;
import io.github.labdev2025cmd.papinho.identity.UserDirectory;
import io.github.labdev2025cmd.papinho.identity.UserDirectory.UserSummary;
import io.github.labdev2025cmd.papinho.media.MediaService;
import io.github.labdev2025cmd.papinho.media.MediaView;
import io.github.labdev2025cmd.papinho.messaging.MessagingExceptions.InvalidMessageException;
import io.github.labdev2025cmd.papinho.messaging.MessagingExceptions.NotFoundException;
import io.github.labdev2025cmd.papinho.profile.ProfileService;
import io.github.labdev2025cmd.papinho.profile.ProfileView;
import tools.jackson.databind.json.JsonMapper;

/**
 * Mensagens: enviar com {@code clientMessageId} idempotente, sincronizar por {@code afterSeq}, histórico,
 * responder, encaminhar, reagir, fixar, favoritar e "Dados da mensagem" (MS-02, MS-04, RT-01, RT-02).
 */
@Service
public class MessageService {

    /** Máximo de alterações devolvidas por chamada de sincronização. */
    public static final int MAX_SYNC = 500;
    /** Máximo de mensagens por página de histórico. */
    public static final int MAX_PAGE = 100;
    /** A partir de quantos encaminhamentos aparece "encaminhada várias vezes" (MS-04). */
    static final int FORWARDED_MANY = 5;
    /** Mensagens fixadas por conversa; fixar uma quarta solta a mais antiga, como no WhatsApp. */
    static final int MAX_PINNED = 3;

    /** Reação: emoji sem letras, dígitos, espaços nem marcação. */
    private static final Pattern EMOJI = Pattern.compile("^[^\\p{L}\\p{N}\\s<>&\"']{1,32}$");
    private static final int PREVIEW_LENGTH = 200;

    private final MessageRepository messages;
    private final MessageReactionRepository reactions;
    private final MessageStarRepository stars;
    private final ConversationService conversations;
    private final SequenceAllocator sequences;
    private final ReceiptLog receipts;
    private final UserDirectory users;
    private final ProfileService profiles;
    private final MediaService media;
    private final HiddenMessages hidden;
    private final InstanceSettingsService settings;
    private final JsonMapper json;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    MessageService(MessageRepository messages, MessageReactionRepository reactions, MessageStarRepository stars,
            ConversationService conversations, SequenceAllocator sequences, ReceiptLog receipts,
            UserDirectory users, ProfileService profiles, MediaService media, HiddenMessages hidden,
            InstanceSettingsService settings, JsonMapper json, ApplicationEventPublisher events, Clock clock) {
        this.messages = messages;
        this.reactions = reactions;
        this.stars = stars;
        this.conversations = conversations;
        this.sequences = sequences;
        this.receipts = receipts;
        this.users = users;
        this.profiles = profiles;
        this.media = media;
        this.hidden = hidden;
        this.settings = settings;
        this.json = json;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Envia uma mensagem de texto. Repetir o mesmo {@code clientMessageId} devolve a mensagem já gravada, sem
     * duplicar (RT-02). Responder a uma solicitação de mensagem é aceitá-la.
     */
    @Transactional
    public SendResult send(UUID me, UUID conversationId, UUID clientMessageId, String body,
            @Nullable UUID replyToId, @Nullable UUID attachmentId) {
        ConversationMember member = conversations.requireMember(me, conversationId);
        sequences.lock(conversationId);
        var existing = messages.findBySenderIdAndClientMessageId(me, clientMessageId);
        if (existing.isPresent()) {
            if (!existing.get().conversationId().equals(conversationId)) {
                throw new MessagingExceptions.ClientMessageIdConflictException();
            }
            return new SendResult(toViews(me, List.of(existing.get())).getFirst(), false);
        }
        Message.Attachment attachment = null;
        if (attachmentId != null) {
            MediaView file = media.requireOwned(me, attachmentId);
            attachment = new Message.Attachment(file.id(), file.kind().name());
        }
        validateBody(body, attachment != null);
        conversations.requireSendable(me, conversationId);
        if (replyToId != null) {
            Message target = messages.findById(replyToId).filter(m -> !m.deleted() && !m.system())
                    .orElseThrow(() -> new InvalidMessageException("reply target outside conversation"));
            if (!target.conversationId().equals(conversationId) && !privateReply(me, conversationId, target)) {
                throw new InvalidMessageException("reply target outside conversation");
            }
        }
        if (member.state() == MemberState.REQUEST) {
            member.changeState(MemberState.ACTIVE);
        }
        Message saved = append(me, conversationId, clientMessageId, body, replyToId, 0, attachment);
        return new SendResult(toViews(me, List.of(saved)).getFirst(), true);
    }

    /** Alterações depois de {@code afterSeq}, em ordem de alteração (RT-01). */
    @Transactional(readOnly = true)
    public SyncResponse sync(UUID me, UUID conversationId, long afterSeq, int limit) {
        conversations.requireMember(me, conversationId);
        int size = Math.clamp(limit, 1, MAX_SYNC);
        List<Message> found = messages.findChangedAfter(conversationId, Math.max(0, afterSeq), Limit.of(size + 1));
        boolean hasMore = found.size() > size;
        List<Message> page = hasMore ? found.subList(0, size) : found;
        long lastSeq = sequences.current(conversationId);
        return new SyncResponse(toViews(me, withoutHidden(me, page)), lastSeq, hasMore);
    }

    /** Página de histórico antes de {@code beforeSeq}, devolvida em ordem crescente de {@code seq}. */
    @Transactional(readOnly = true)
    public HistoryResponse history(UUID me, UUID conversationId, @Nullable Long beforeSeq, int limit) {
        conversations.requireMember(me, conversationId);
        int size = Math.clamp(limit, 1, MAX_PAGE);
        long before = beforeSeq == null ? Long.MAX_VALUE : beforeSeq;
        List<Message> found = messages.findBefore(conversationId, before, Limit.of(size + 1));
        boolean hasMore = found.size() > size;
        List<Message> page = new ArrayList<>(hasMore ? found.subList(0, size) : found);
        page.sort(Comparator.comparingLong(Message::seq));
        return new HistoryResponse(toViews(me, withoutHidden(me, page)), sequences.current(conversationId),
                hasMore);
    }

    /**
     * Encaminha mensagens (em ordem) para conversas; o selo conta os saltos e vira "encaminhada várias
     * vezes" a partir de {@value #FORWARDED_MANY} (MS-04).
     */
    @Transactional
    public List<MessageView> forward(UUID me, List<UUID> messageIds, List<UUID> conversationIds) {
        List<Message> sources = new ArrayList<>();
        for (UUID id : new LinkedHashSet<>(messageIds)) {
            Message message = messages.findById(id).orElseThrow(NotFoundException::new);
            conversations.requireMember(me, message.conversationId());
            if (message.deleted() || message.system()) {
                throw new InvalidMessageException("deleted message");
            }
            sources.add(message);
        }
        sources.sort(Comparator.comparing(Message::createdAt).thenComparingLong(Message::seq));
        List<Message> created = new ArrayList<>();
        for (UUID target : new LinkedHashSet<>(conversationIds)) {
            ConversationMember member = conversations.requireSendable(me, target);
            if (member.state() == MemberState.REQUEST) {
                member.changeState(MemberState.ACTIVE);
            }
            sequences.lock(target);
            for (Message source : sources) {
                created.add(append(me, target, UUID.randomUUID(), source.body(), null, source.forwardCount() + 1,
                        source.attachment()));
            }
        }
        return toViews(me, created);
    }

    /** Reage (ou troca a reação); {@code emoji} nulo remove (MS-04). */
    @Transactional
    public MessageView react(UUID me, UUID messageId, @Nullable String emoji) {
        Message message = requireVisible(me, messageId);
        if ((message.deleted() || message.system()) && emoji != null) {
            throw new InvalidMessageException("deleted message");
        }
        sequences.lock(message.conversationId());
        MessageReaction.Key key = new MessageReaction.Key(messageId, me);
        if (emoji == null) {
            if (!reactions.existsById(key)) {
                return toViews(me, List.of(message)).getFirst();
            }
            reactions.deleteById(key);
        } else {
            if (!EMOJI.matcher(emoji).matches()) {
                throw new InvalidMessageException("reaction is not an emoji");
            }
            Instant now = clock.instant();
            reactions.findById(key).ifPresentOrElse(r -> r.change(emoji, now),
                    () -> reactions.save(new MessageReaction(messageId, me, emoji, now)));
        }
        reactions.flush();
        touch(message);
        return toViews(me, List.of(message)).getFirst();
    }

    /** Fixa ou solta uma mensagem na conversa; no máximo {@value #MAX_PINNED} fixadas (MS-04). */
    @Transactional
    public MessageView pin(UUID me, UUID messageId, boolean pinned) {
        Message message = requireVisible(me, messageId);
        sequences.lock(message.conversationId());
        Instant now = clock.instant();
        if (pinned && (message.deleted() || message.system())) {
            throw new InvalidMessageException("deleted message");
        }
        if (pinned && message.pinnedAt() == null) {
            List<Message> current = messages.findPinned(message.conversationId());
            for (int i = 0; i <= current.size() - MAX_PINNED; i++) {
                current.get(i).unpin(sequences.nextChange(message.conversationId()));
            }
            message.pin(now, sequences.nextChange(message.conversationId()));
            notifyChanged(message.conversationId());
        } else if (!pinned && message.pinnedAt() != null) {
            message.unpin(sequences.nextChange(message.conversationId()));
            notifyChanged(message.conversationId());
        }
        return toViews(me, List.of(message)).getFirst();
    }

    @Transactional(readOnly = true)
    public List<MessageView> pinned(UUID me, UUID conversationId) {
        conversations.requireMember(me, conversationId);
        return toViews(me, withoutHidden(me, messages.findPinned(conversationId)));
    }

    /**
     * Galeria da conversa (MD-03): mídia (fotos e vídeos), documentos, áudios ou links, da mais nova para a mais
     * antiga, paginada por {@code beforeSeq}.
     */
    @Transactional(readOnly = true)
    public HistoryResponse gallery(UUID me, UUID conversationId, GalleryTab tab, @Nullable Long beforeSeq,
            int limit) {
        conversations.requireMember(me, conversationId);
        int size = Math.clamp(limit, 1, MAX_PAGE);
        long before = beforeSeq == null ? Long.MAX_VALUE : beforeSeq;
        List<Message> found = switch (tab) {
            case MEDIA -> messages.findWithAttachments(conversationId, List.of("IMAGE", "VIDEO"), before,
                    Limit.of(size + 1));
            case DOCUMENTS -> messages.findWithAttachments(conversationId, List.of("DOCUMENT"), before,
                    Limit.of(size + 1));
            case AUDIO -> messages.findWithAttachments(conversationId, List.of("AUDIO"), before, Limit.of(size + 1));
            case LINKS -> messages.findWithLinks(conversationId, before, Limit.of(size + 1));
        };
        boolean hasMore = found.size() > size;
        return new HistoryResponse(toViews(me, withoutHidden(me, hasMore ? found.subList(0, size) : found)),
                sequences.current(conversationId), hasMore);
    }

    /** Abas da galeria da conversa (MD-03). */
    public enum GalleryTab {
        MEDIA, LINKS, DOCUMENTS, AUDIO
    }

    /** Favorita só para mim: não avança o seq da conversa nem avisa ninguém. */
    @Transactional
    public MessageView star(UUID me, UUID messageId, boolean starred) {
        Message message = requireVisible(me, messageId);
        MessageStar.Key key = new MessageStar.Key(me, messageId);
        if (starred && !stars.existsById(key)) {
            stars.save(new MessageStar(me, messageId, clock.instant()));
        } else if (!starred) {
            stars.deleteById(key);
        }
        stars.flush();
        return toViews(me, List.of(message)).getFirst();
    }

    /** Mensagens favoritas de todas as conversas que ainda posso ver, da mais recente favoritada. */
    @Transactional(readOnly = true)
    public List<MessageView> starred(UUID me) {
        List<UUID> ids = stars.findByUser(me).stream().map(MessageStar::messageId).toList();
        Map<UUID, Message> found = messages.findAllById(ids).stream()
                .collect(Collectors.toMap(Message::id, Function.identity()));
        List<Message> visible = ids.stream().map(found::get).filter(Objects::nonNull)
                .filter(m -> !m.deleted() && isVisible(me, m.conversationId())).toList();
        return toViews(me, withoutHidden(me, visible));
    }

    /** "Dados da mensagem": quando cada destinatário recebeu e leu. Só o autor vê (MS-02). */
    @Transactional(readOnly = true)
    public MessageInfo info(UUID me, UUID messageId) {
        Message message = requireVisible(me, messageId);
        if (!me.equals(message.senderId())) {
            throw new NotFoundException();
        }
        Map<UUID, Instant> delivered = receipts.coveredAt(message.conversationId(), ReceiptLog.Kind.DELIVERED,
                message.seq());
        Map<UUID, Instant> read = receipts.coveredAt(message.conversationId(), ReceiptLog.Kind.READ, message.seq());
        List<ConversationMember> others = conversations.membersOf(message.conversationId()).stream()
                .filter(m -> !m.userId().equals(me))
                .filter(m -> m.state() == MemberState.ACTIVE || m.state() == MemberState.REQUEST).toList();
        Set<UUID> ids = others.stream().map(ConversationMember::userId).collect(Collectors.toSet());
        Map<UUID, UserSummary> summaries = users.findAllById(ids).stream()
                .collect(Collectors.toMap(UserSummary::id, Function.identity()));
        Map<UUID, ProfileView> views = profiles.viewAll(me, ids);
        List<RecipientInfo> recipients = others.stream().filter(m -> summaries.containsKey(m.userId()))
                .map(m -> {
                    ProfileView view = views.get(m.userId());
                    // CT-02: leitura escondida enquanto a outra pessoa não aceitou a conversa.
                    @Nullable Instant readAt = m.state() == MemberState.ACTIVE ? read.get(m.userId()) : null;
                    return new RecipientInfo(m.userId(), summaries.get(m.userId()).username(),
                            view == null ? summaries.get(m.userId()).username() : view.displayName(),
                            delivered.get(m.userId()), readAt);
                }).toList();
        return new MessageInfo(toViews(me, List.of(message)).getFirst(), recipients);
    }

    /**
     * Cópia das últimas mensagens de uma conversa individual para uma denúncia (CT-04): só quem participa pede,
     * e a cópia indica a outra pessoa da conversa como denunciada.
     */
    @Transactional(readOnly = true)
    public ReportSnapshot reportSnapshot(UUID me, UUID conversationId, int count) {
        if (conversations.requireConversation(me, conversationId).kind() != ConversationKind.DIRECT) {
            throw new NotFoundException();
        }
        UUID peer = conversations.membersOf(conversationId).stream().map(ConversationMember::userId)
                .filter(id -> !id.equals(me)).findFirst().orElseThrow(NotFoundException::new);
        List<Message> latest = new ArrayList<>(messages.findBefore(conversationId, Long.MAX_VALUE,
                Limit.of(Math.clamp(count, 1, MAX_PAGE))));
        latest.sort(Comparator.comparingLong(Message::seq));
        return new ReportSnapshot(peer, latest.stream().filter(m -> !m.deleted() && !m.system())
                .map(m -> new SnapshotMessage(m.id(), m.senderId(), m.body(), m.createdAt())).toList());
    }

    /**
     * Edita o texto (ou a legenda) de uma mensagem própria dentro da janela configurada pelo admin (MS-05). O
     * selo "editada" vem de {@code editedAt}.
     */
    @Transactional
    public MessageView edit(UUID me, UUID messageId, String body) {
        Message message = requireVisible(me, messageId);
        requireAuthorWithin(me, message, settings.current().messageEditWindow());
        validateBody(body, message.attachment() != null);
        if (!body.equals(message.body())) {
            sequences.lock(message.conversationId());
            message.edit(body, clock.instant(), sequences.nextChange(message.conversationId()));
            messages.flush();
            notifyChanged(message.conversationId());
        }
        return toViews(me, List.of(message)).getFirst();
    }

    /**
     * Apaga para todos (MS-06): só o autor, dentro da janela configurada. O texto, o anexo (se nenhuma outra
     * mensagem o usa), as reações e as favoritas saem do servidor; fica o aviso "Esta mensagem foi apagada".
     */
    @Transactional
    public MessageView deleteForEveryone(UUID me, UUID messageId) {
        Message message = requireVisible(me, messageId);
        if (message.deleted()) {
            return toViews(me, List.of(message)).getFirst();
        }
        requireAuthorWithin(me, message, settings.current().messageDeleteWindow());
        sequences.lock(message.conversationId());
        Message.Attachment removed = message.deleteForEveryone(clock.instant(),
                sequences.nextChange(message.conversationId()));
        reactions.deleteByMessage(messageId);
        stars.deleteByMessage(messageId);
        messages.flush();
        if (removed != null && !messages.existsByAttachmentId(removed.id())) {
            media.delete(removed.id());
        }
        notifyChanged(message.conversationId());
        return toViews(me, List.of(message)).getFirst();
    }

    /** Apaga para mim (MS-06): some só da minha tela, em todos os meus dispositivos. */
    @Transactional
    public void hide(UUID me, List<UUID> messageIds) {
        Map<UUID, List<UUID>> byConversation = new HashMap<>();
        Instant now = clock.instant();
        for (UUID id : new LinkedHashSet<>(messageIds)) {
            Message message = requireVisible(me, id);
            hidden.hide(me, id, now);
            byConversation.computeIfAbsent(message.conversationId(), key -> new ArrayList<>()).add(id);
        }
        byConversation.forEach((conversationId, ids) -> events
                .publishEvent(new MessagingEvents.MessagesHidden(conversationId, List.copyOf(ids), me)));
    }

    /**
     * Aviso do sistema na conversa (GR-01): entra na sequência como uma mensagem e chega a todos pelo
     * {@code afterSeq}.
     */
    void appendSystem(UUID conversationId, UUID actor, SystemNotice notice) {
        Instant now = clock.instant();
        long seq = sequences.next(conversationId, now);
        messages.saveAndFlush(new Message(conversationId, seq, MessageKind.SYSTEM, actor, UUID.randomUUID(),
                json.writeValueAsString(notice), null, 0, null, now));
        conversations.membersOf(conversationId).stream().filter(m -> m.userId().equals(actor)).findFirst()
                .ifPresent(m -> m.advanceRead(seq));
        notifyChanged(conversationId);
    }

    /**
     * "Responder em privado" (GR-01): a conversa individual pode citar uma mensagem de um grupo de que eu participo
     * quando quem a escreveu é a outra pessoa desta conversa.
     */
    private boolean privateReply(UUID me, UUID conversationId, Message target) {
        if (target.senderId() == null || target.senderId().equals(me)) {
            return false;
        }
        boolean inDirect = conversations.membersOf(conversationId).stream()
                .anyMatch(m -> m.userId().equals(target.senderId()));
        return inDirect && isVisible(me, target.conversationId())
                && conversations.requireConversation(me, conversationId).kind() == ConversationKind.DIRECT;
    }

    private void requireAuthorWithin(UUID me, Message message, java.time.Duration window) {
        if (!me.equals(message.senderId()) || message.deleted() || message.system()) {
            throw new MessagingExceptions.NotAuthorException();
        }
        if (message.createdAt().plus(window).isBefore(clock.instant())) {
            throw new MessagingExceptions.WindowExpiredException();
        }
    }

    private List<Message> withoutHidden(UUID me, List<Message> page) {
        Set<UUID> mine = hidden.hiddenAmong(me, page.stream().map(Message::id).toList());
        return mine.isEmpty() ? page : page.stream().filter(m -> !mine.contains(m.id())).toList();
    }

    private Message append(UUID me, UUID conversationId, UUID clientMessageId, String body,
            @Nullable UUID replyToId, int forwardCount, Message.@Nullable Attachment attachment) {
        Instant now = clock.instant();
        long seq = sequences.next(conversationId, now);
        Message saved = messages.saveAndFlush(
                new Message(conversationId, seq, me, clientMessageId, body, replyToId, forwardCount, attachment,
                        now));
        // Quem envia já "leu" a própria mensagem: o marcador de não lidas começa depois dela.
        conversations.requireMember(me, conversationId).advanceRead(seq);
        notifyChanged(conversationId);
        return saved;
    }

    private void touch(Message message) {
        message.changed(sequences.nextChange(message.conversationId()));
        notifyChanged(message.conversationId());
    }

    private void notifyChanged(UUID conversationId) {
        events.publishEvent(new MessagingEvents.MessagesChanged(conversationId, sequences.current(conversationId),
                conversations.audience(conversationId)));
    }

    private Message requireVisible(UUID me, UUID messageId) {
        Message message = messages.findById(messageId).orElseThrow(NotFoundException::new);
        conversations.requireMember(me, message.conversationId());
        return message;
    }

    private boolean isVisible(UUID me, UUID conversationId) {
        try {
            conversations.requireMember(me, conversationId);
            return true;
        } catch (NotFoundException hidden) {
            return false;
        }
    }

    private static void validateBody(String body, boolean hasAttachment) {
        if (body.isBlank() && !hasAttachment) {
            throw new InvalidMessageException("blank body");
        }
        if (body.length() > Message.MAX_BODY_LENGTH) {
            throw new InvalidMessageException("body too long");
        }
    }

    private List<MessageView> toViews(UUID me, List<Message> page) {
        if (page.isEmpty()) {
            return List.of();
        }
        Set<UUID> ids = page.stream().map(Message::id).collect(Collectors.toSet());
        Map<UUID, List<ReactionView>> reactionsByMessage = new HashMap<>();
        for (MessageReaction reaction : reactions.findByMessages(ids)) {
            reactionsByMessage.computeIfAbsent(reaction.messageId(), id -> new ArrayList<>())
                    .add(new ReactionView(reaction.userId(), reaction.emoji()));
        }
        Set<UUID> starred = stars.findByUserAndMessages(me, ids).stream().map(MessageStar::messageId)
                .collect(Collectors.toSet());
        Set<UUID> replyIds = new HashSet<>();
        page.forEach(m -> {
            if (m.replyToId() != null) {
                replyIds.add(m.replyToId());
            }
        });
        Map<UUID, Message> replies = messages.findAllById(replyIds).stream()
                .collect(Collectors.toMap(Message::id, Function.identity()));
        Set<UUID> attachmentIds = new HashSet<>();
        page.forEach(m -> {
            if (m.attachment() != null) {
                attachmentIds.add(m.attachment().id());
            }
        });
        replies.values().forEach(m -> {
            if (m.attachment() != null) {
                attachmentIds.add(m.attachment().id());
            }
        });
        Map<UUID, MediaView> files = media.describe(attachmentIds);
        return page.stream().map(m -> {
            Message reply = m.replyToId() == null ? null : replies.get(m.replyToId());
            ReplyPreview replyTo = reply == null ? null
                    : new ReplyPreview(reply.id(), reply.conversationId(), reply.seq(), reply.senderId(),
                            preview(reply.body()), reply.attachment() == null ? null : reply.attachment().kind(),
                            reply.deleted());
            MediaView attachment = m.attachment() == null ? null : files.get(m.attachment().id());
            return new MessageView(m.id(), m.conversationId(), m.seq(), m.changeSeq(), m.kind(), m.senderId(),
                    m.clientMessageId(), m.body(), m.createdAt(), replyTo, m.forwardCount(),
                    m.forwardCount() >= FORWARDED_MANY, m.pinnedAt(),
                    reactionsByMessage.getOrDefault(m.id(), List.of()), starred.contains(m.id()),
                    attachment == null ? null : AttachmentView.of(attachment), m.editedAt(), m.deleted());
        }).toList();
    }

    private static String preview(String body) {
        return body.length() <= PREVIEW_LENGTH ? body : body.substring(0, PREVIEW_LENGTH);
    }

    /** Resultado do envio; {@code created} falso quando o {@code clientMessageId} já existia. */
    public record SendResult(MessageView message, boolean created) {
    }

    /**
     * Mensagem como o cliente a vê.
     *
     * @param changeSeq seq da última alteração; o cliente guarda o maior para o próximo {@code afterSeq}
     * @param forwardCount quantas vezes foi encaminhada até aqui (0 = original)
     * @param starred favoritada por quem pediu
     * @param editedAt quando foi editada pela última vez (selo "editada", MS-05)
     * @param deleted apagada para todos: sem texto nem anexo, mostra "Esta mensagem foi apagada" (MS-06)
     */
    public record MessageView(UUID id, UUID conversationId, long seq, long changeSeq, MessageKind kind,
            @Nullable UUID senderId,
            UUID clientMessageId, String body, Instant createdAt, @Nullable ReplyPreview replyTo, int forwardCount,
            boolean forwardedMany, @Nullable Instant pinnedAt, List<ReactionView> reactions, boolean starred,
            @Nullable AttachmentView attachment, @Nullable Instant editedAt, boolean deleted) {
    }

    /** Anexo como o cliente o vê; o conteúdo vem de {@code /api/v1/media/{id}/content}. */
    public record AttachmentView(UUID id, String kind, String contentType, long size, String filename,
            @Nullable Integer width, @Nullable Integer height, @Nullable String thumbhash, boolean hasThumbnail) {

        static AttachmentView of(MediaView view) {
            return new AttachmentView(view.id(), view.kind().name(), view.contentType(), view.size(),
                    view.filename(), view.width(), view.height(), view.thumbhash(), view.hasThumbnail());
        }
    }

    /** Trecho da mensagem respondida, para o salto até a original. */
    public record ReplyPreview(UUID id, UUID conversationId, long seq, @Nullable UUID senderId, String body,
            @Nullable String attachmentKind, boolean deleted) {
    }

    public record ReactionView(UUID userId, String emoji) {
    }

    /** @param lastSeq seq atual da conversa; com {@code hasMore}, peça de novo a partir do último changeSeq */
    public record SyncResponse(List<MessageView> messages, long lastSeq, boolean hasMore) {
    }

    public record HistoryResponse(List<MessageView> messages, long lastSeq, boolean hasMore) {
    }

    public record MessageInfo(MessageView message, List<RecipientInfo> recipients) {
    }

    /** Últimas mensagens de uma conversa para denúncia, da mais antiga para a mais nova. */
    public record ReportSnapshot(UUID peerId, List<SnapshotMessage> messages) {
    }

    public record SnapshotMessage(UUID id, @Nullable UUID senderId, String body, Instant sentAt) {
    }

    /** Destinatário no "Dados da mensagem"; horários nulos quando ainda não recebeu ou leu. */
    public record RecipientInfo(UUID userId, String username, String displayName, @Nullable Instant deliveredAt,
            @Nullable Instant readAt) {
    }
}
