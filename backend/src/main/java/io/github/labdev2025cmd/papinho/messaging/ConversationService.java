package io.github.labdev2025cmd.papinho.messaging;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
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
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.labdev2025cmd.papinho.contact.ContactQueries;
import io.github.labdev2025cmd.papinho.identity.UserDirectory;
import io.github.labdev2025cmd.papinho.identity.UserDirectory.UserSummary;
import io.github.labdev2025cmd.papinho.messaging.MessagingExceptions.NotFoundException;
import io.github.labdev2025cmd.papinho.profile.ProfileService;
import io.github.labdev2025cmd.papinho.profile.ProfileView;

/**
 * Conversas: abrir a individual e as "Mensagens salvas" (MS-12), listar (com grupos, GR-01), solicitações de
 * mensagem (CT-02) e ponteiros de entrega, leitura e "marcar como não lida" (MS-02, MS-14).
 */
@Service
public class ConversationService {

    private static final Set<MemberState> VISIBLE = EnumSet.of(MemberState.ACTIVE, MemberState.REQUEST);

    private final ConversationRepository conversations;
    private final ConversationMemberRepository members;
    private final GroupDetailsRepository groups;
    private final Broadcasts broadcasts;
    private final ConversationQueries queries;
    private final SequenceAllocator sequences;
    private final ReceiptLog receipts;
    private final UserDirectory users;
    private final ProfileService profiles;
    private final ContactQueries contacts;
    private final MessagingPreferences preferences;
    private final NamedParameterJdbcTemplate jdbc;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    ConversationService(ConversationRepository conversations, ConversationMemberRepository members,
            GroupDetailsRepository groups, Broadcasts broadcasts, ConversationQueries queries, SequenceAllocator sequences,
            ReceiptLog receipts, UserDirectory users, ProfileService profiles, ContactQueries contacts,
            MessagingPreferences preferences, NamedParameterJdbcTemplate jdbc, ApplicationEventPublisher events,
            Clock clock) {
        this.conversations = conversations;
        this.members = members;
        this.groups = groups;
        this.broadcasts = broadcasts;
        this.queries = queries;
        this.sequences = sequences;
        this.receipts = receipts;
        this.users = users;
        this.profiles = profiles;
        this.contacts = contacts;
        this.preferences = preferences;
        this.jdbc = jdbc;
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
        return view(me, directWith(me, other.id()));
    }

    /**
     * Conversa individual de {@code me} com {@code otherId}, criada se preciso (com o timer padrão de quem abriu,
     * MS-07). Para a outra pessoa nasce como solicitação se {@code me} não estiver nos contatos dela (CT-02).
     */
    UUID directWith(UUID me, UUID otherId) {
        String key = Conversation.directKey(me, otherId);
        sequences.lockKey(key);
        Conversation conversation = conversations.findByDirectKey(key).orElseGet(() -> {
            Instant now = clock.instant();
            Conversation direct = Conversation.direct(me, otherId, now);
            direct.changeDisappearing(preferences.defaultDisappearing(me));
            Conversation created = conversations.saveAndFlush(direct);
            members.save(new ConversationMember(created.id(), me, MemberState.ACTIVE, now));
            MemberState otherState = contacts.isContact(otherId, me) ? MemberState.ACTIVE : MemberState.REQUEST;
            members.save(new ConversationMember(created.id(), otherId, otherState, now));
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
        return conversation.id();
    }

    /** "Mensagens salvas" (MS-12): a conversa consigo mesmo, criada na primeira vez. */
    @Transactional
    public ConversationView openSelf(UUID me) {
        String key = Conversation.selfKey(me);
        sequences.lockKey(key);
        Conversation conversation = conversations.findByDirectKey(key).orElseGet(() -> {
            Conversation created = conversations.saveAndFlush(Conversation.self(me, clock.instant()));
            members.saveAndFlush(new ConversationMember(created.id(), me, MemberState.ACTIVE, clock.instant()));
            return created;
        });
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

    /** Num grupo com "só admins editam os dados", só admins (GR-01, MS-07). */
    void requireGroupEditor(ConversationMember member) {
        GroupDetails group = groups.findById(member.conversationId()).orElseThrow(NotFoundException::new);
        if (group.onlyAdminsEditInfo() && !member.admin()) {
            throw new MessagingExceptions.OnlyAdminsException();
        }
    }

    /**
     * Guarda (ou apaga, com texto vazio) o meu rascunho na conversa e avisa os meus outros dispositivos (MS-11).
     */
    @Transactional
    public void saveDraft(UUID me, UUID conversationId, String body) {
        requireMember(me, conversationId);
        MapSqlParameterSource params = new MapSqlParameterSource("userId", me)
                .addValue("conversationId", conversationId).addValue("body", body)
                .addValue("now", clock.instant().atOffset(java.time.ZoneOffset.UTC));
        if (body.isBlank()) {
            jdbc.update("delete from message_drafts where user_id = :userId and conversation_id = :conversationId",
                    params);
        } else {
            jdbc.update("insert into message_drafts (user_id, conversation_id, body, updated_at) "
                    + "values (:userId, :conversationId, :body, :now) on conflict (user_id, conversation_id) "
                    + "do update set body = excluded.body, updated_at = excluded.updated_at", params);
        }
        events.publishEvent(new MessagingEvents.ConversationUpdated(conversationId, Set.of(me)));
    }

    /** Enviar apaga o rascunho daquela conversa. */
    void clearDraft(UUID me, UUID conversationId) {
        int removed = jdbc.update(
                "delete from message_drafts where user_id = :userId and conversation_id = :conversationId",
                new MapSqlParameterSource("userId", me).addValue("conversationId", conversationId));
        if (removed > 0) {
            events.publishEvent(new MessagingEvents.ConversationUpdated(conversationId, Set.of(me)));
        }
    }

    /** Conversa pelo id, sem checar participação (quem chama já checou). */
    Conversation find(UUID conversationId) {
        return conversations.findById(conversationId).orElseThrow(NotFoundException::new);
    }

    /** Conversa de que {@code me} participa (ativa ou solicitação); senão 404. */
    Conversation requireConversation(UUID me, UUID conversationId) {
        requireMember(me, conversationId);
        return conversations.findById(conversationId).orElseThrow(NotFoundException::new);
    }

    /**
     * Participação de quem vai escrever: sem bloqueio na conversa individual (CT-03) e, num grupo com "só admins
     * enviam", só admins (GR-01).
     */
    ConversationMember requireSendable(UUID me, UUID conversationId) {
        ConversationMember member = requireMember(me, conversationId);
        Conversation conversation = conversations.findById(conversationId).orElseThrow(NotFoundException::new);
        if (conversation.kind() == ConversationKind.GROUP) {
            GroupDetails group = groups.findById(conversationId).orElseThrow(NotFoundException::new);
            if (group.onlyAdminsSend() && !member.admin()) {
                throw new MessagingExceptions.OnlyAdminsException();
            }
        } else if (conversation.kind() == ConversationKind.BROADCAST) {
            Broadcasts.Details details = broadcasts.details(List.of(conversationId)).get(conversationId);
            if (details == null || details.expired(clock.instant())) {
                throw new MessagingExceptions.BroadcastExpiredException();
            }
        } else if (blockedWithSomeone(me, conversationId)) {
            throw new MessagingExceptions.BlockedException();
        }
        return member;
    }

    /** Quem deve ser avisado de mudanças nas mensagens: todos menos quem recusou. */
    Set<UUID> audience(UUID conversationId) {
        return members.findByConversation(conversationId).stream().filter(m -> VISIBLE.contains(m.state()))
                .map(ConversationMember::userId).collect(Collectors.toSet());
    }

    List<ConversationMember> membersOf(UUID conversationId) {
        return members.findByConversation(conversationId);
    }

    /**
     * Há bloqueio entre {@code me} e o outro participante de uma conversa individual? Em grupo o bloqueio não
     * impede de escrever para o grupo (como no WhatsApp).
     */
    boolean blockedWithSomeone(UUID me, UUID conversationId) {
        boolean direct = conversations.findById(conversationId).map(c -> c.kind() == ConversationKind.DIRECT)
                .orElse(false);
        return direct && members.findByConversation(conversationId).stream().map(ConversationMember::userId)
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
        Set<UUID> peerIds = new HashSet<>();
        Set<UUID> groupIds = new HashSet<>();
        Set<UUID> broadcastIds = new HashSet<>();
        rows.forEach(row -> {
            if (row.kind() == ConversationKind.BROADCAST) {
                broadcastIds.add(row.id());
            } else if (row.kind() == ConversationKind.GROUP) {
                groupIds.add(row.id());
            } else if (row.kind() == ConversationKind.SELF) {
                peerIds.add(me);
            } else if (row.peerId() != null) {
                peerIds.add(row.peerId());
            }
        });
        Map<UUID, UserSummary> summaries = users.findAllById(peerIds).stream()
                .collect(Collectors.toMap(UserSummary::id, Function.identity()));
        Map<UUID, ProfileView> views = profiles.viewAll(me, peerIds);
        Map<UUID, @Nullable String> nicknames = contacts.contactNicknames(me, peerIds);
        Map<UUID, GroupDetails> details = groups.findAllById(groupIds).stream()
                .collect(Collectors.toMap(GroupDetails::conversationId, Function.identity()));
        // Num grupo a prévia mostra quem mandou a última mensagem ("Ana: oi").
        Set<UUID> lastSenders = rows.stream().filter(row -> row.kind() == ConversationKind.GROUP)
                .map(ConversationQueries.Row::last).filter(last -> last != null && last.senderId() != null)
                .map(last -> last.senderId()).filter(id -> !id.equals(me)).collect(Collectors.toSet());
        Map<UUID, String> senderLabels = labels(me, lastSenders);
        Map<UUID, Broadcasts.Details> broadcastDetails = broadcasts.details(broadcastIds);
        List<ConversationView> result = new ArrayList<>();
        Instant now = clock.instant();
        for (ConversationQueries.Row row : rows) {
            if (row.kind() == ConversationKind.BROADCAST) {
                Broadcasts.Details list = broadcastDetails.get(row.id());
                if (list != null) {
                    BroadcastView broadcast = new BroadcastView(list.name(), list.recipientCount(),
                            list.expiresAt(), list.messageTtlSeconds(), list.expired(now));
                    result.add(new ConversationView(row.id(), row.kind().name(), row.state(), null, null,
                            broadcast, row.lastSeq(), preview(row, Map.of()), 0, null, false, row.readSeq(),
                            row.lastSeq(), row.lastSeq(), row.updatedAt(), !broadcast.expired(), false, null,
                            row.draft()));
                }
                continue;
            }
            if (row.kind() == ConversationKind.GROUP) {
                GroupDetails group = details.get(row.id());
                if (group != null) {
                    result.add(toGroupView(row, group, senderLabels));
                }
                continue;
            }
            UUID peerId = row.kind() == ConversationKind.SELF ? me : row.peerId();
            if (peerId != null && summaries.containsKey(peerId)) {
                result.add(toView(me, row, summaries.get(peerId), views.get(peerId), nicknames));
            }
        }
        return result;
    }

    private ConversationView toView(UUID me, ConversationQueries.Row row, UserSummary peer,
            @Nullable ProfileView profile, Map<UUID, @Nullable String> nicknames) {
        String displayName = profile == null ? peer.username() : profile.displayName();
        String nickname = nicknames.get(peer.id());
        boolean contact = nicknames.containsKey(peer.id());
        PeerView peerView = new PeerView(peer.id(), peer.username(), displayName,
                nickname != null ? nickname : displayName, profile != null && profile.hasPhoto(),
                profile == null ? Instant.EPOCH : profile.updatedAt(), peer.active(), contact);
        boolean self = row.kind() == ConversationKind.SELF;
        boolean blockedByMe = !self && contacts.hasBlocked(me, peer.id());
        // CT-02: antes de a outra pessoa aceitar, quem enviou não vê a confirmação de leitura.
        @Nullable Long peerReadSeq = null;
        if (self) {
            peerReadSeq = row.lastSeq();
        } else if (row.peerState() == MemberState.ACTIVE) {
            peerReadSeq = row.peerReadSeq();
        }
        long peerDeliveredSeq = self ? row.lastSeq() : row.peerDeliveredSeq();
        return new ConversationView(row.id(), row.kind().name(), row.state(), peerView, null, null, row.lastSeq(),
                preview(row, Map.of()), row.unreadCount(), row.firstUnreadSeq(), row.markedUnread(), row.readSeq(),
                peerDeliveredSeq, peerReadSeq, row.updatedAt(), !blockedByMe && peer.active(), blockedByMe,
                row.disappearingSeconds(), row.draft());
    }

    /** Nome como {@code me} vê cada pessoa: apelido do contato, nome de exibição ou {@code @usuario}. */
    Map<UUID, String> labels(UUID me, Set<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ProfileView> views = profiles.viewAll(me, ids);
        Map<UUID, @Nullable String> nicknames = contacts.contactNicknames(me, ids);
        Map<UUID, String> result = new java.util.HashMap<>();
        users.findAllById(ids).forEach(user -> {
            String nickname = nicknames.get(user.id());
            ProfileView view = views.get(user.id());
            result.put(user.id(), nickname != null ? nickname : view != null ? view.displayName() : user.username());
        });
        return result;
    }

    private ConversationView toGroupView(ConversationQueries.Row row, GroupDetails group,
            Map<UUID, String> senderLabels) {
        boolean admin = row.role() == MemberRole.ADMIN;
        GroupView groupView = new GroupView(group.name(), group.description(), group.photoMediaId(),
                group.updatedAt(), row.memberCount(), row.role(), group.onlyAdminsSend(), group.onlyAdminsEditInfo(),
                group.onlyAdminsAddMembers(), group.joinApproval(), !group.onlyAdminsEditInfo() || admin,
                !group.onlyAdminsAddMembers() || admin);
        boolean canSend = row.state() == MemberState.ACTIVE && (!group.onlyAdminsSend() || admin);
        return new ConversationView(row.id(), row.kind().name(), row.state(), null, groupView, null, row.lastSeq(),
                preview(row, senderLabels), row.unreadCount(), row.firstUnreadSeq(), row.markedUnread(), row.readSeq(),
                row.peerDeliveredSeq(), row.peerReadSeq(), row.updatedAt(), canSend, false,
                row.disappearingSeconds(), row.draft());
    }

    private static @Nullable MessagePreview preview(ConversationQueries.Row row, Map<UUID, String> senderLabels) {
        ConversationQueries.LastMessage last = row.last();
        return last == null ? null
                : new MessagePreview(last.id(), last.seq(), last.senderId(),
                        last.senderId() == null ? null : senderLabels.get(last.senderId()), last.body(),
                        last.createdAt(), last.attachmentKind(), last.deleted(), last.kind());
    }

    /**
     * Conversa vista por um participante.
     *
     * @param kind DIRECT, GROUP ou SELF ("Mensagens salvas")
     * @param state ACTIVE ou REQUEST (solicitação de mensagem para mim)
     * @param peer a outra pessoa (conversa individual) ou eu mesmo (mensagens salvas); nulo em grupo
     * @param group dados do grupo; nulo nas outras
     * @param broadcast dados da lista de transmissão (GR-03); nulo nas outras
     * @param readSeq até onde eu li
     * @param peerDeliveredSeq até onde a outra pessoa recebeu; num grupo, até onde todos receberam
     * @param peerReadSeq até onde a outra pessoa leu (num grupo, todos); nulo enquanto ela não aceitou a conversa
     *     (CT-02)
     * @param canSend posso escrever (não bloqueei, a conta dela está ativa; no grupo, as permissões)
     * @param disappearingSeconds timer das mensagens temporárias (MS-07); nulo = desligado
     * @param draft meu rascunho nesta conversa, sincronizado entre os meus dispositivos (MS-11)
     */
    public record ConversationView(UUID id, String kind, MemberState state, @Nullable PeerView peer,
            @Nullable GroupView group, @Nullable BroadcastView broadcast, long lastSeq, @Nullable MessagePreview lastMessage, long unreadCount,
            @Nullable Long firstUnreadSeq, boolean markedUnread, long readSeq, long peerDeliveredSeq,
            @Nullable Long peerReadSeq, Instant updatedAt, boolean canSend, boolean blockedByMe,
            @Nullable Integer disappearingSeconds, @Nullable String draft) {
    }

    /**
     * Lista de transmissão vista pelo dono (GR-03, GR-04).
     *
     * @param expiresAt quando a lista deixa de enviar (GR-04); nulo = não expira
     * @param messageTtlSeconds mensagens enviadas por ela são temporárias com este timer (GR-04)
     * @param expired já passou de {@code expiresAt}
     */
    public record BroadcastView(String name, long recipientCount, @Nullable Instant expiresAt,
            @Nullable Integer messageTtlSeconds, boolean expired) {
    }

    /**
     * Grupo visto por um membro (GR-01).
     *
     * @param photoId arquivo da foto do grupo (miniatura em {@code /api/v1/media/{id}/thumbnail})
     * @param photoVersion versão para cache
     * @param myRole meu papel
     * @param canEditInfo posso mudar nome, descrição e foto
     * @param canAddMembers posso adicionar membros
     */
    public record GroupView(String name, String description, @Nullable UUID photoId, Instant photoVersion,
            long memberCount, MemberRole myRole, boolean onlyAdminsSend, boolean onlyAdminsEditInfo,
            boolean onlyAdminsAddMembers, boolean joinApproval, boolean canEditInfo, boolean canAddMembers) {
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
     * @param senderLabel nome de quem mandou, só em grupo e quando não fui eu
     * @param kind USER ou SYSTEM (aviso do grupo, corpo em JSON)
     */
    public record MessagePreview(UUID id, long seq, @Nullable UUID senderId, @Nullable String senderLabel,
            String body, Instant createdAt,
            @Nullable String attachmentKind, boolean deleted, MessageKind kind) {
    }
}
