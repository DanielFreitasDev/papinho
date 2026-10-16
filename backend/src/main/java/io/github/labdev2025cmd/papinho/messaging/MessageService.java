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
import io.github.labdev2025cmd.papinho.shared.security.RateLimiter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Mensagens: enviar com {@code clientMessageId} idempotente, sincronizar por {@code afterSeq}, histórico,
 * responder, encaminhar, reagir, fixar, favoritar e "Dados da mensagem" (MS-02, MS-04, RT-01, RT-02).
 */
@Service
public class MessageService {

    /** Máximo de alterações devolvidas por chamada de sincronização. */
    public static final int MAX_SYNC = 500;
    /** Visualização única (MS-08): tipos aceitos, validade do token e prazo para o arquivo sair. */
    static final Set<String> VIEW_ONCE_KINDS = Set.of("IMAGE", "VIDEO", "AUDIO");
    static final java.time.Duration VIEW_ONCE_TOKEN_TTL = java.time.Duration.ofMinutes(5);
    static final int VIEW_ONCE_DAYS = 14;
    /** Limites da enquete (MS-09), como os do WhatsApp. */
    static final int MAX_POLL_QUESTION = 300;
    static final int MAX_POLL_OPTION = 100;
    static final int MAX_POLL_OPTIONS = 12;
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
    private final MessagingPreferences preferences;
    private final Polls polls;
    private final ViewOnceOpens viewOnceOpens;
    private final Broadcasts broadcasts;
    private final StickerService stickers;
    private final RateLimiter rateLimiter;
    private final io.github.labdev2025cmd.papinho.contact.ContactQueries contactsOf;
    private final java.security.SecureRandom random = new java.security.SecureRandom();
    private final InstanceSettingsService settings;
    private final JsonMapper json;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    MessageService(MessageRepository messages, MessageReactionRepository reactions, MessageStarRepository stars,
            ConversationService conversations, SequenceAllocator sequences, ReceiptLog receipts,
            UserDirectory users, ProfileService profiles, MediaService media, HiddenMessages hidden,
            MessagingPreferences preferences, Polls polls, ViewOnceOpens viewOnceOpens, Broadcasts broadcasts,
            StickerService stickers, RateLimiter rateLimiter,
            io.github.labdev2025cmd.papinho.contact.ContactQueries contactsOf,
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
        this.preferences = preferences;
        this.polls = polls;
        this.viewOnceOpens = viewOnceOpens;
        this.broadcasts = broadcasts;
        this.stickers = stickers;
        this.rateLimiter = rateLimiter;
        this.contactsOf = contactsOf;
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
        return send(me, conversationId, clientMessageId, body, replyToId, attachmentId, SendOptions.NONE);
    }

    /**
     * Envia com opções: {@code viewOnce} torna o anexo (foto, vídeo ou áudio ainda não usado em outra mensagem) de
     * visualização única (MS-08); {@code silent} chega sem notificação (MS-11). Enviar apaga o rascunho.
     */
    @Transactional
    public SendResult send(UUID me, UUID conversationId, UUID clientMessageId, String body,
            @Nullable UUID replyToId, @Nullable UUID attachmentId, SendOptions options) {
        boolean viewOnce = options.viewOnce();
        ConversationMember member = conversations.requireMember(me, conversationId);
        sequences.lock(conversationId);
        var existing = messages.findBySenderIdAndClientMessageId(me, clientMessageId);
        if (existing.isPresent()) {
            if (!existing.get().conversationId().equals(conversationId)) {
                throw new MessagingExceptions.ClientMessageIdConflictException();
            }
            return new SendResult(toViews(me, List.of(existing.get())).getFirst(), false);
        }
        // Reenvio idempotente não gasta o limite; só mensagens novas (SG-03).
        rateLimiter.consume(RateLimiter.Policy.MESSAGE_BY_USER, me.toString());
        Message.Attachment attachment = null;
        if (attachmentId != null) {
            MediaView file = media.requireOwned(me, attachmentId);
            attachment = new Message.Attachment(file.id(), file.kind().name());
        }
        UUID stickerId = options.stickerId();
        if (stickerId != null) {
            if (attachment != null || !body.isEmpty()) {
                throw new InvalidMessageException("sticker goes alone");
            }
            stickers.requireAvailable(stickerId);
        }
        validateBody(body, attachment != null || stickerId != null);
        conversations.requireSendable(me, conversationId);
        ConversationKind kind = conversations.find(conversationId).kind();
        if (viewOnce && (attachment == null || !VIEW_ONCE_KINDS.contains(attachment.kind())
                || messages.existsByAttachmentId(attachment.id())
                || kind == ConversationKind.SELF || kind == ConversationKind.BROADCAST)) {
            throw new InvalidMessageException("view once needs a new photo, video or audio");
        }
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
        Message saved = append(me, conversationId, MessageKind.USER, clientMessageId, body, replyToId, 0, attachment,
                stickerId);
        if (viewOnce) {
            saved.markViewOnce();
        }
        if (options.silent()) {
            saved.markSilent();
        }
        messages.flush();
        conversations.clearDraft(me, conversationId);
        if (kind == ConversationKind.BROADCAST) {
            fanOut(me, saved, options.silent());
        }
        return new SendResult(toViews(me, List.of(saved)).getFirst(), true);
    }

    /**
     * Abre uma mensagem de visualização única (MS-08): emite, uma única vez por destinatário, um token de curta
     * duração para o conteúdo. Quem enviou não abre; quem já abriu recebe 410.
     */
    @Transactional
    public ViewOnceGrant openViewOnce(UUID me, UUID messageId) {
        Message message = requireVisible(me, messageId);
        if (!message.viewOnce() || me.equals(message.senderId())) {
            throw new InvalidMessageException("not a view once message for you");
        }
        Message.Attachment attachment = message.attachment();
        if (attachment == null) {
            throw new MessagingExceptions.ViewOnceGoneException();
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        if (!viewOnceOpens.open(messageId, me, sha256(token), now, now.plus(VIEW_ONCE_TOKEN_TTL))) {
            throw new MessagingExceptions.ViewOnceGoneException();
        }
        sequences.lock(message.conversationId());
        touch(message);
        MediaView file = media.describe(List.of(attachment.id())).get(attachment.id());
        return new ViewOnceGrant(token, attachment.kind(), file == null ? "" : file.contentType());
    }

    /** Conteúdo pelo token (de quem o recebeu e enquanto vale); senão 404. */
    @Transactional(readOnly = true)
    public MediaService.Content viewOnceContent(UUID me, String token) throws java.io.IOException {
        UUID messageId = viewOnceOpens.messageOfToken(sha256(token), me, clock.instant())
                .orElseThrow(NotFoundException::new);
        Message message = messages.findById(messageId).orElseThrow(NotFoundException::new);
        Message.Attachment attachment = message.attachment();
        if (attachment == null) {
            throw new NotFoundException();
        }
        return media.grantedContent(attachment.id());
    }

    /**
     * Tira do servidor os arquivos de visualização única que todos já abriram (com os tokens vencidos) ou que
     * passaram de {@value #VIEW_ONCE_DAYS} dias (MS-08). Devolve quantos tirou.
     */
    @Transactional
    public int purgeViewOnce(int limit) {
        Instant now = clock.instant();
        List<UUID> ids = viewOnceOpens.purgeable(now, now.minus(java.time.Duration.ofDays(VIEW_ONCE_DAYS)), limit);
        for (Message message : messages.findAllById(ids)) {
            sequences.lock(message.conversationId());
            UUID file = message.purgeViewOnce(sequences.nextChange(message.conversationId()));
            messages.flush();
            if (file != null && !messages.existsByAttachmentId(file)) {
                media.delete(file);
            }
            notifyChanged(message.conversationId());
        }
        return ids.size();
    }

    /**
     * Lista de transmissão (GR-03): copia a mensagem para a conversa individual de cada destinatário que tem o
     * dono nos contatos (e sem bloqueio). Com timer na lista, a cópia e o original são temporários (GR-04).
     */
    private void fanOut(UUID me, Message original, boolean silent) {
        Broadcasts.Details details = broadcasts.details(List.of(original.conversationId()))
                .get(original.conversationId());
        if (details == null) {
            return;
        }
        Integer ttl = details.messageTtlSeconds();
        if (ttl != null) {
            original.expireAt(original.createdAt().plusSeconds(ttl));
        }
        for (UUID recipient : broadcasts.recipients(original.conversationId())) {
            if (!contactsOf.isContact(recipient, me) || contactsOf.isBlockedEitherWay(me, recipient)) {
                continue;
            }
            UUID direct = conversations.directWith(me, recipient);
            sequences.lock(direct);
            Message copy = append(me, direct, MessageKind.USER, UUID.randomUUID(), original.body(), null,
                    original.forwardCount(), original.attachment(), original.stickerId());
            if (ttl != null) {
                copy.expireAt(copy.createdAt().plusSeconds(ttl));
            }
            if (silent) {
                copy.markSilent();
            }
        }
        messages.flush();
    }

    /** Opções de envio: visualização única (MS-08) e silenciosa (MS-11). */
    public record SendOptions(boolean viewOnce, boolean silent, @Nullable UUID stickerId) {
        public static final SendOptions NONE = new SendOptions(false, false, null);

        public SendOptions(boolean viewOnce, boolean silent) {
            this(viewOnce, silent, null);
        }
    }

    /** Token para abrir o conteúdo em {@code /api/v1/view-once/{token}}. */
    public record ViewOnceGrant(String token, String kind, String contentType) {
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Alterações depois de {@code afterSeq}, em ordem de alteração (RT-01). */
    @Transactional(readOnly = true)
    public SyncResponse sync(UUID me, UUID conversationId, long afterSeq, int limit) {
        conversations.requireReadable(me, conversationId);
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
        conversations.requireReadable(me, conversationId);
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
            conversations.requireReadable(me, message.conversationId());
            if (message.deleted() || message.kind() != MessageKind.USER || message.viewOnce()) {
                throw new InvalidMessageException("cannot forward");
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
            boolean broadcast = conversations.find(target).kind() == ConversationKind.BROADCAST;
            for (Message source : sources) {
                Message copy = append(me, target, MessageKind.USER, UUID.randomUUID(), source.body(), null,
                        source.forwardCount() + 1, source.attachment(), source.stickerId());
                created.add(copy);
                if (broadcast) {
                    fanOut(me, copy, false);
                }
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
        conversations.requireReadable(me, conversationId);
        return toViews(me, withoutHidden(me, messages.findPinned(conversationId)));
    }

    /**
     * Galeria da conversa (MD-03): mídia (fotos e vídeos), documentos, áudios ou links, da mais nova para a mais
     * antiga, paginada por {@code beforeSeq}.
     */
    @Transactional(readOnly = true)
    public HistoryResponse gallery(UUID me, UUID conversationId, GalleryTab tab, @Nullable Long beforeSeq,
            int limit) {
        conversations.requireReadable(me, conversationId);
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
        // PV-06: só vê leituras quem compartilha a própria, e só de quem também compartilha.
        Set<UUID> sharers = conversations.receiptSharers(message.conversationId());
        boolean iShare = sharers.contains(me);
        List<RecipientInfo> recipients = others.stream().filter(m -> summaries.containsKey(m.userId()))
                .map(m -> {
                    ProfileView view = views.get(m.userId());
                    // CT-02: leitura escondida enquanto a outra pessoa não aceitou a conversa.
                    @Nullable Instant readAt = m.state() == MemberState.ACTIVE && iShare
                            && sharers.contains(m.userId()) ? read.get(m.userId()) : null;
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
        conversations.requireReadable(me, conversationId);
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
        if (message.kind() == MessageKind.POLL) {
            throw new InvalidMessageException("poll cannot be edited");
        }
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
        polls.delete(messageId);
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
     * Cria uma enquete (MS-09): pergunta, de 2 a 12 opções, escolha única ou múltipla, anônima ou não e data de
     * encerramento opcional. O mesmo {@code clientMessageId} devolve a já criada (RT-02).
     */
    @Transactional
    public SendResult createPoll(UUID me, UUID conversationId, UUID clientMessageId, String question,
            List<String> options, boolean multiple, boolean anonymous, @Nullable Instant closesAt) {
        conversations.requireMember(me, conversationId);
        sequences.lock(conversationId);
        var existing = messages.findBySenderIdAndClientMessageId(me, clientMessageId);
        if (existing.isPresent()) {
            if (!existing.get().conversationId().equals(conversationId)) {
                throw new MessagingExceptions.ClientMessageIdConflictException();
            }
            return new SendResult(toViews(me, List.of(existing.get())).getFirst(), false);
        }
        rateLimiter.consume(RateLimiter.Policy.MESSAGE_BY_USER, me.toString());
        String cleanQuestion = question.strip();
        List<String> cleanOptions = options.stream().map(String::strip).toList();
        if (cleanQuestion.isEmpty() || cleanQuestion.length() > MAX_POLL_QUESTION || cleanOptions.size() < 2
                || cleanOptions.size() > MAX_POLL_OPTIONS
                || cleanOptions.stream().anyMatch(o -> o.isEmpty() || o.length() > MAX_POLL_OPTION)
                || new HashSet<>(cleanOptions).size() != cleanOptions.size()) {
            throw new InvalidMessageException("poll");
        }
        if (closesAt != null && !closesAt.isAfter(clock.instant())) {
            throw new InvalidMessageException("poll closes in the past");
        }
        ConversationMember member = conversations.requireSendable(me, conversationId);
        if (conversations.find(conversationId).kind() == ConversationKind.BROADCAST) {
            throw new InvalidMessageException("polls are not sent to broadcast lists");
        }
        if (member.state() == MemberState.REQUEST) {
            member.changeState(MemberState.ACTIVE);
        }
        Message saved = append(me, conversationId, MessageKind.POLL, clientMessageId, cleanQuestion, null, 0, null,
                null);
        polls.create(saved.id(), multiple, anonymous, closesAt, cleanOptions);
        return new SendResult(toViews(me, List.of(saved)).getFirst(), true);
    }

    /** Vota (ou troca o voto); lista vazia tira o voto. Enquete encerrada não aceita votos (MS-09). */
    @Transactional
    public MessageView vote(UUID me, UUID messageId, List<UUID> optionIds) {
        Message message = requireVisible(me, messageId);
        Polls.Poll poll = message.kind() == MessageKind.POLL && !message.deleted()
                ? polls.find(List.of(messageId)).get(messageId) : null;
        if (poll == null) {
            throw new InvalidMessageException("not a poll");
        }
        if (poll.closesAt() != null && !poll.closesAt().isAfter(clock.instant())) {
            throw new MessagingExceptions.PollClosedException();
        }
        Set<UUID> chosen = new LinkedHashSet<>(optionIds);
        Set<UUID> valid = poll.options().stream().map(Polls.Option::id).collect(Collectors.toSet());
        if (!valid.containsAll(chosen) || (!poll.multiple() && chosen.size() > 1)) {
            throw new InvalidMessageException("poll options");
        }
        sequences.lock(message.conversationId());
        polls.vote(messageId, me, chosen, clock.instant());
        touch(message);
        return toViews(me, List.of(message)).getFirst();
    }

    /**
     * Liga, troca ou desliga o timer de mensagens temporárias da conversa (MS-07); vale para as mensagens enviadas
     * daqui em diante e deixa um aviso na conversa. Num grupo com "só admins editam os dados", só admins.
     */
    @Transactional
    public void changeDisappearing(UUID me, UUID conversationId, @Nullable Integer seconds) {
        ConversationMember member = conversations.requireMember(me, conversationId);
        if (seconds != null && !MessagingPreferences.TIMERS.contains(seconds)) {
            throw new InvalidMessageException("timer");
        }
        Conversation conversation = conversations.find(conversationId);
        if (conversation.kind() == ConversationKind.GROUP) {
            conversations.requireGroupEditor(member);
        } else if (conversation.kind() == ConversationKind.DIRECT
                && conversations.blockedWithSomeone(me, conversationId)) {
            throw new MessagingExceptions.BlockedException();
        }
        if (java.util.Objects.equals(conversation.disappearingSeconds(), seconds)) {
            return;
        }
        sequences.lock(conversationId);
        conversation.changeDisappearing(seconds);
        if (member.state() == MemberState.REQUEST) {
            member.changeState(MemberState.ACTIVE);
        }
        appendSystem(conversationId, me, new SystemNotice("timer",
                new SystemNotice.Person(me, profiles.own(me).displayName()), List.of(),
                seconds == null ? null : seconds.toString()));
        events.publishEvent(new MessagingEvents.ConversationUpdated(conversationId,
                conversations.audience(conversationId)));
    }

    @Transactional(readOnly = true)
    public Preferences preferences(UUID me) {
        return new Preferences(preferences.defaultDisappearing(me), preferences.keepArchived(me));
    }

    /**
     * Timer padrão para conversas novas (MS-07) e "manter arquivadas ao receber nova mensagem" (GR-05; nulo mantém
     * a escolha atual).
     */
    @Transactional
    public Preferences changePreferences(UUID me, @Nullable Integer defaultDisappearingSeconds,
            @Nullable Boolean keepArchived) {
        if (defaultDisappearingSeconds != null && !MessagingPreferences.TIMERS.contains(defaultDisappearingSeconds)) {
            throw new InvalidMessageException("timer");
        }
        preferences.changeDefaultDisappearing(me, defaultDisappearingSeconds, clock.instant());
        if (keepArchived != null) {
            preferences.changeKeepArchived(me, keepArchived, clock.instant());
        }
        return preferences(me);
    }

    /**
     * Apaga até {@code limit} mensagens temporárias vencidas (MS-07): a linha, as reações, as favoritas e o arquivo
     * (quando nenhuma outra mensagem o usa). Devolve quantas apagou.
     */
    @Transactional
    public int purgeExpired(int limit) {
        List<Message> expired = messages.findExpired(clock.instant(), Limit.of(limit));
        if (expired.isEmpty()) {
            return 0;
        }
        Set<UUID> files = new HashSet<>();
        Set<UUID> touched = new HashSet<>();
        for (Message message : expired) {
            if (message.attachment() != null) {
                files.add(message.attachment().id());
            }
            touched.add(message.conversationId());
        }
        messages.deleteAll(expired);
        messages.flush();
        files.stream().filter(id -> !messages.existsByAttachmentId(id)).forEach(media::delete);
        touched.forEach(id -> events.publishEvent(new MessagingEvents.ConversationUpdated(id,
                conversations.audience(id))));
        return expired.size();
    }

    /**
     * @param defaultDisappearingSeconds timer das conversas novas; nulo = desligado
     * @param keepArchived conversas arquivadas continuam arquivadas quando chega mensagem nova (GR-05)
     */
    public record Preferences(@Nullable Integer defaultDisappearingSeconds, @Nullable Boolean keepArchived) {
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

    /** Mensagens pelos ids, na mesma ordem (quem chama já checou o acesso). */
    List<MessageView> views(UUID me, List<UUID> ids) {
        Map<UUID, Message> found = messages.findAllById(ids).stream()
                .collect(Collectors.toMap(Message::id, Function.identity()));
        return toViews(me, ids.stream().map(found::get).filter(Objects::nonNull).toList());
    }

    private List<Message> withoutHidden(UUID me, List<Message> page) {
        Set<UUID> mine = hidden.hiddenAmong(me, page.stream().map(Message::id).toList());
        return mine.isEmpty() ? page : page.stream().filter(m -> !mine.contains(m.id())).toList();
    }

    private Message append(UUID me, UUID conversationId, UUID clientMessageId, String body,
            @Nullable UUID replyToId, int forwardCount, Message.@Nullable Attachment attachment) {
        return append(me, conversationId, MessageKind.USER, clientMessageId, body, replyToId, forwardCount,
                attachment, null);
    }

    private Message append(UUID me, UUID conversationId, MessageKind kind, UUID clientMessageId, String body,
            @Nullable UUID replyToId, int forwardCount, Message.@Nullable Attachment attachment,
            @Nullable UUID stickerId) {
        Instant now = clock.instant();
        long seq = sequences.next(conversationId, now);
        Message message = new Message(conversationId, seq, kind, me, clientMessageId, body, replyToId, forwardCount,
                attachment, now);
        message.useSticker(stickerId);
        Integer timer = conversations.find(conversationId).disappearingSeconds();
        if (timer != null) {
            message.expireAt(now.plusSeconds(timer));
        }
        Message saved = messages.saveAndFlush(message);
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
        conversations.requireReadable(me, message.conversationId());
        return message;
    }

    private boolean isVisible(UUID me, UUID conversationId) {
        try {
            conversations.requireReadable(me, conversationId);
            return true;
        } catch (NotFoundException | MessagingExceptions.ConversationLockedException hidden) {
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
        Map<UUID, Polls.Poll> pollsById = polls.find(page.stream().filter(m -> m.kind() == MessageKind.POLL)
                .map(Message::id).toList());
        List<UUID> viewOnceIds = page.stream().filter(Message::viewOnce).map(Message::id).toList();
        Set<UUID> openedByMe = viewOnceOpens.openedBy(me, viewOnceIds);
        Map<UUID, Long> openCounts = viewOnceOpens.counts(viewOnceIds);
        Map<UUID, UUID> stickerMedia = stickers.mediaOf(page.stream().map(Message::stickerId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        Instant now = clock.instant();
        return page.stream().map(m -> {
            Message reply = m.replyToId() == null ? null : replies.get(m.replyToId());
            ReplyPreview replyTo = reply == null ? null
                    : new ReplyPreview(reply.id(), reply.conversationId(), reply.seq(), reply.senderId(),
                            preview(reply.body()), reply.attachment() == null ? null : reply.attachment().kind(),
                            reply.deleted());
            // O id do arquivo de visualização única nunca vai para o cliente: só abre por token (MS-08).
            MediaView attachment = m.attachment() == null || m.viewOnce() ? null : files.get(m.attachment().id());
            ViewOnceView viewOnce = !m.viewOnce() ? null
                    : new ViewOnceView(m.attachmentKind() == null ? "IMAGE" : m.attachmentKind(),
                            openedByMe.contains(m.id()), openCounts.getOrDefault(m.id(), 0L), m.attachment() == null);
            return new MessageView(m.id(), m.conversationId(), m.seq(), m.changeSeq(), m.kind(), m.senderId(),
                    m.clientMessageId(), m.body(), m.createdAt(), replyTo, m.forwardCount(),
                    m.forwardCount() >= FORWARDED_MANY, m.pinnedAt(),
                    reactionsByMessage.getOrDefault(m.id(), List.of()), starred.contains(m.id()),
                    attachment == null ? null : AttachmentView.of(attachment), m.editedAt(), m.deleted(),
                    m.expiresAt(), PollView.of(pollsById.get(m.id()), me, now), viewOnce, m.silent(),
                    m.stickerId() == null || !stickerMedia.containsKey(m.stickerId()) ? null
                            : new StickerRef(m.stickerId(), stickerMedia.get(m.stickerId())));
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
     * @param expiresAt quando a mensagem temporária some (MS-07); o cliente esconde as vencidas antes do job
     * @param poll enquete (kind POLL, MS-09); a pergunta é o {@code body}
     */
    public record MessageView(UUID id, UUID conversationId, long seq, long changeSeq, MessageKind kind,
            @Nullable UUID senderId,
            UUID clientMessageId, String body, Instant createdAt, @Nullable ReplyPreview replyTo, int forwardCount,
            boolean forwardedMany, @Nullable Instant pinnedAt, List<ReactionView> reactions, boolean starred,
            @Nullable AttachmentView attachment, @Nullable Instant editedAt, boolean deleted,
            @Nullable Instant expiresAt, @Nullable PollView poll, @Nullable ViewOnceView viewOnce,
            boolean silent, @Nullable StickerRef sticker) {
    }

    /** Figurinha da mensagem (MS-10); a imagem vem de {@code /api/v1/media/{mediaId}/content}. */
    public record StickerRef(UUID id, UUID mediaId) {
    }

    /**
     * Mensagem de visualização única (MS-08), sem o arquivo.
     *
     * @param kind IMAGE, VIDEO ou AUDIO
     * @param openedByMe já abri (não abre de novo)
     * @param openedCount quantas pessoas abriram (para quem enviou)
     * @param expired o arquivo já saiu do servidor
     */
    public record ViewOnceView(String kind, boolean openedByMe, long openedCount, boolean expired) {
    }

    /**
     * Enquete como o cliente a vê (MS-09).
     *
     * @param voters quem votou em cada opção; vazio quando a enquete é anônima
     * @param totalVoters quantas pessoas votaram
     * @param myVotes opções em que eu votei
     */
    public record PollView(boolean multiple, boolean anonymous, @Nullable Instant closesAt, boolean closed,
            List<PollOptionView> options, long totalVoters, List<UUID> myVotes) {

        static @Nullable PollView of(Polls.@Nullable Poll poll, UUID me, Instant now) {
            if (poll == null) {
                return null;
            }
            Set<UUID> everyone = new HashSet<>();
            List<UUID> mine = new ArrayList<>();
            List<PollOptionView> options = poll.options().stream().map(option -> {
                everyone.addAll(option.voters());
                if (option.voters().contains(me)) {
                    mine.add(option.id());
                }
                return new PollOptionView(option.id(), option.text(), option.voters().size(),
                        poll.anonymous() ? List.of() : List.copyOf(option.voters()));
            }).toList();
            boolean closed = poll.closesAt() != null && !poll.closesAt().isAfter(now);
            return new PollView(poll.multiple(), poll.anonymous(), poll.closesAt(), closed, options, everyone.size(),
                    mine);
        }
    }

    public record PollOptionView(UUID id, String text, long votes, List<UUID> voters) {
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
