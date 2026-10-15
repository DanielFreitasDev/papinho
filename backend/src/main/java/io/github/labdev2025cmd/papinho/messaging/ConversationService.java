package io.github.labdev2025cmd.papinho.messaging;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.labdev2025cmd.papinho.contact.ContactQueries;
import io.github.labdev2025cmd.papinho.identity.UserDirectory;
import io.github.labdev2025cmd.papinho.identity.UserDirectory.UserSummary;
import io.github.labdev2025cmd.papinho.messaging.MessagingExceptions.NotFoundException;
import io.github.labdev2025cmd.papinho.profile.ProfileService;
import io.github.labdev2025cmd.papinho.profile.ProfileView;

/**
 * Conversas individuais: abrir, listar, solicitações de mensagem (CT-02) e ponteiros de entrega, leitura e
 * "marcar como não lida" (MS-02, MS-14).
 */
@Service
public class ConversationService {

    private static final Set<MemberState> VISIBLE = EnumSet.of(MemberState.ACTIVE, MemberState.REQUEST);

    private final ConversationRepository conversations;
    private final ConversationMemberRepository members;
    private final ConversationQueries queries;
    private final SequenceAllocator sequences;
    private final ReceiptLog receipts;
    private final UserDirectory users;
    private final ProfileService profiles;
    private final ContactQueries contacts;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    ConversationService(ConversationRepository conversations, ConversationMemberRepository members,
            ConversationQueries queries, SequenceAllocator sequences, ReceiptLog receipts, UserDirectory users,
            ProfileService profiles, ContactQueries contacts, ApplicationEventPublisher events, Clock clock) {
        this.conversations = conversations;
        this.members = members;
        this.queries = queries;
        this.sequences = sequences;
        this.receipts = receipts;
        this.users = users;
        this.profiles = profiles;
        this.contacts = contacts;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Abre (ou devolve) a conversa individual com alguém. Para a outra pessoa ela nasce como solicitação de
     * mensagem se quem abriu não estiver nos contatos dela (CT-02); só aparece quando houver mensagem.
     */
    @Transactional
    public ConversationView openDirect(UUID me, String username) {
        UserSummary other = users.findByUsername(username).orElseThrow(NotFoundException::new);
        if (other.id().equals(me)) {
            throw new MessagingExceptions.SelfConversationException();
        }
        String key = Conversation.directKey(me, other.id());
        sequences.lockKey(key);
        Conversation conversation = conversations.findByDirectKey(key).orElseGet(() -> {
            Instant now = clock.instant();
            Conversation created = conversations.saveAndFlush(Conversation.direct(me, other.id(), now));
            members.save(new ConversationMember(created.id(), me, MemberState.ACTIVE, now));
            MemberState otherState = contacts.isContact(other.id(), me) ? MemberState.ACTIVE : MemberState.REQUEST;
            members.save(new ConversationMember(created.id(), other.id(), otherState, now));
            members.flush();
            return created;
        });
        ConversationMember mine = members.findById(new ConversationMember.Key(conversation.id(), me))
                .orElseThrow(NotFoundException::new);
        if (mine.state() == MemberState.DECLINED) {
            // Quem recusou e depois procura a pessoa de novo está aceitando a conversa.
            mine.changeState(MemberState.ACTIVE);
            members.flush();
        }
        return view(me, conversation.id());
    }

    /** Conversas normais ou, com {@code requests}, as solicitações de mensagem pendentes. */
    @Transactional(readOnly = true)
    public List<ConversationView> list(UUID me, boolean requests) {
        return toViews(me, queries.list(me, EnumSet.of(requests ? MemberState.REQUEST : MemberState.ACTIVE), null));
    }

    @Transactional(readOnly = true)
    public ConversationView view(UUID me, UUID conversationId) {
        return toViews(me, queries.list(me, VISIBLE, conversationId)).stream().findFirst()
                .orElseThrow(NotFoundException::new);
    }

    @Transactional
    public ConversationView accept(UUID me, UUID conversationId) {
        changeOwnState(me, conversationId, MemberState.ACTIVE);
        return view(me, conversationId);
    }

    /** Recusa a solicitação: a conversa some da lista e não avisa a outra pessoa. */
    @Transactional
    public void decline(UUID me, UUID conversationId) {
        changeOwnState(me, conversationId, MemberState.DECLINED);
    }

    /** Este dispositivo recebeu as mensagens até {@code seq}. */
    @Transactional
    public void markDelivered(UUID me, UUID conversationId, long seq) {
        ConversationMember member = requireMember(me, conversationId);
        long upTo = Math.min(seq, sequences.current(conversationId));
        if (member.advanceDelivered(upTo)) {
            receipts.record(conversationId, me, ReceiptLog.Kind.DELIVERED, upTo, clock.instant());
            publishReceipts(member);
        }
    }

    /** A pessoa leu até {@code seq}; também limpa "marcar como não lida" (MS-14). */
    @Transactional
    public void markRead(UUID me, UUID conversationId, long seq) {
        ConversationMember member = requireMember(me, conversationId);
        long upTo = Math.min(seq, sequences.current(conversationId));
        long deliveredBefore = member.deliveredSeq();
        boolean wasMarked = member.markedUnread();
        boolean changed = member.advanceRead(upTo);
        Instant now = clock.instant();
        if (member.deliveredSeq() > deliveredBefore) {
            receipts.record(conversationId, me, ReceiptLog.Kind.DELIVERED, member.deliveredSeq(), now);
        }
        if (changed) {
            receipts.record(conversationId, me, ReceiptLog.Kind.READ, upTo, now);
        }
        if (changed || member.deliveredSeq() > deliveredBefore) {
            publishReceipts(member);
        }
        if (wasMarked) {
            events.publishEvent(new MessagingEvents.ConversationUpdated(conversationId, Set.of(me)));
        }
    }

    @Transactional
    public void markUnread(UUID me, UUID conversationId) {
        requireMember(me, conversationId).markUnread();
        events.publishEvent(new MessagingEvents.ConversationUpdated(conversationId, Set.of(me)));
    }

    /** Participação visível (ativa ou solicitação) de {@code me}; senão 404, sem revelar se a conversa existe. */
    ConversationMember requireMember(UUID me, UUID conversationId) {
        return members.findById(new ConversationMember.Key(conversationId, me))
                .filter(member -> VISIBLE.contains(member.state())).orElseThrow(NotFoundException::new);
    }

    /** Quem deve ser avisado de mudanças nas mensagens: todos menos quem recusou. */
    Set<UUID> audience(UUID conversationId) {
        return members.findByConversation(conversationId).stream().filter(m -> VISIBLE.contains(m.state()))
                .map(ConversationMember::userId).collect(Collectors.toSet());
    }

    List<ConversationMember> membersOf(UUID conversationId) {
        return members.findByConversation(conversationId);
    }

    /** Há bloqueio entre {@code me} e algum outro participante? */
    boolean blockedWithSomeone(UUID me, UUID conversationId) {
        return members.findByConversation(conversationId).stream().map(ConversationMember::userId)
                .filter(id -> !id.equals(me)).anyMatch(id -> contacts.isBlockedEitherWay(me, id));
    }

    private void changeOwnState(UUID me, UUID conversationId, MemberState state) {
        ConversationMember member = requireMember(me, conversationId);
        member.changeState(state);
        members.flush();
        Set<UUID> recipients = new HashSet<>(audience(conversationId));
        recipients.add(me);
        events.publishEvent(new MessagingEvents.ConversationUpdated(conversationId, recipients));
        // Ao aceitar, quem enviou passa a ver a leitura que estava escondida.
        publishReceipts(member);
    }

    /**
     * Avisa os outros participantes do avanço dos ponteiros de quem leu (com a leitura escondida enquanto for
     * solicitação, CT-02) e os outros dispositivos de quem leu, que só precisam atualizar a conversa.
     */
    private void publishReceipts(ConversationMember member) {
        Set<UUID> others = new HashSet<>(audience(member.conversationId()));
        others.remove(member.userId());
        long readSeq = member.state() == MemberState.ACTIVE ? member.readSeq() : 0;
        events.publishEvent(new MessagingEvents.ReceiptsChanged(member.conversationId(), member.userId(),
                member.deliveredSeq(), readSeq, others));
        events.publishEvent(new MessagingEvents.ConversationUpdated(member.conversationId(),
                Set.of(member.userId())));
    }

    /** Pode {@code me} mostrar "digitando" nesta conversa? Só participante ativo, sem bloqueio (CT-02, CT-03). */
    @Transactional(readOnly = true)
    public Set<UUID> typingAudience(UUID me, UUID conversationId) {
        ConversationMember member = requireMember(me, conversationId);
        if (member.state() != MemberState.ACTIVE || blockedWithSomeone(me, conversationId)) {
            return Set.of();
        }
        Set<UUID> others = new HashSet<>(audience(conversationId));
        others.remove(me);
        return others;
    }

    /**
     * Pessoas com quem {@code userId} tem conversa individual aceita pelos dois lados e sem bloqueio: quem pode
     * ver a presença dele por enquanto (RT-03; a visibilidade configurável entra com PV-04).
     */
    @Transactional(readOnly = true)
    public Set<UUID> mutualPeers(UUID userId) {
        return queries.mutualPeers(userId).stream().filter(peer -> !contacts.isBlockedEitherWay(userId, peer))
                .collect(Collectors.toSet());
    }

    private List<ConversationView> toViews(UUID me, List<ConversationQueries.Row> rows) {
        Set<UUID> peerIds = rows.stream().map(ConversationQueries.Row::peerId).filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<UUID, UserSummary> summaries = users.findAllById(peerIds).stream()
                .collect(Collectors.toMap(UserSummary::id, Function.identity()));
        Map<UUID, ProfileView> views = profiles.viewAll(me, peerIds);
        Map<UUID, @Nullable String> nicknames = contacts.contactNicknames(me, peerIds);
        return rows.stream().filter(row -> row.peerId() != null && summaries.containsKey(row.peerId()))
                .map(row -> toView(me, row, summaries.get(row.peerId()), views.get(row.peerId()), nicknames))
                .toList();
    }

    private ConversationView toView(UUID me, ConversationQueries.Row row, UserSummary peer,
            @Nullable ProfileView profile, Map<UUID, @Nullable String> nicknames) {
        String displayName = profile == null ? peer.username() : profile.displayName();
        String nickname = nicknames.get(peer.id());
        boolean contact = nicknames.containsKey(peer.id());
        PeerView peerView = new PeerView(peer.id(), peer.username(), displayName,
                nickname != null ? nickname : displayName, profile != null && profile.hasPhoto(),
                profile == null ? Instant.EPOCH : profile.updatedAt(), peer.active(), contact);
        boolean blockedByMe = contacts.hasBlocked(me, peer.id());
        // CT-02: antes de a outra pessoa aceitar, quem enviou não vê a confirmação de leitura.
        @Nullable Long peerReadSeq = row.peerState() == MemberState.ACTIVE ? row.peerReadSeq() : null;
        ConversationQueries.LastMessage last = row.last();
        MessagePreview preview = last == null ? null
                : new MessagePreview(last.id(), last.seq(), last.senderId(), last.body(), last.createdAt(),
                        last.attachmentKind(), last.deleted());
        return new ConversationView(row.id(), row.kind().name(), row.state(), peerView, row.lastSeq(), preview,
                row.unreadCount(), row.firstUnreadSeq(), row.markedUnread(), row.readSeq(), row.peerDeliveredSeq(),
                peerReadSeq, row.updatedAt(), !blockedByMe && peer.active(), blockedByMe);
    }

    /**
     * Conversa vista por um participante.
     *
     * @param state ACTIVE ou REQUEST (solicitação de mensagem para mim)
     * @param readSeq até onde eu li
     * @param peerReadSeq até onde a outra pessoa leu; nulo enquanto ela não aceitou a conversa (CT-02)
     * @param canSend posso escrever (não bloqueei e a conta dela está ativa)
     */
    public record ConversationView(UUID id, String kind, MemberState state, PeerView peer, long lastSeq,
            @Nullable MessagePreview lastMessage, long unreadCount, @Nullable Long firstUnreadSeq,
            boolean markedUnread, long readSeq, long peerDeliveredSeq, @Nullable Long peerReadSeq,
            Instant updatedAt, boolean canSend, boolean blockedByMe) {
    }

    /** A outra pessoa da conversa individual, com o apelido local como {@code label}. */
    public record PeerView(UUID userId, String username, String displayName, String label, boolean hasPhoto,
            Instant photoVersion, boolean active, boolean contact) {
    }

    /**
     * Última mensagem para a prévia da lista (corpo cortado em 200 caracteres).
     *
     * @param attachmentKind tipo do anexo (IMAGE, VIDEO, AUDIO, DOCUMENT), para "📷 Foto" e afins
     * @param deleted apagada para todos (MS-06)
     */
    public record MessagePreview(UUID id, long seq, @Nullable UUID senderId, String body, Instant createdAt,
            @Nullable String attachmentKind, boolean deleted) {
    }
}
