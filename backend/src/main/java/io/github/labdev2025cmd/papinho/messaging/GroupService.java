package io.github.labdev2025cmd.papinho.messaging;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
import io.github.labdev2025cmd.papinho.media.MediaKind;
import io.github.labdev2025cmd.papinho.media.MediaService;
import io.github.labdev2025cmd.papinho.media.MediaView;
import io.github.labdev2025cmd.papinho.messaging.MessagingExceptions.CannotAddException;
import io.github.labdev2025cmd.papinho.messaging.MessagingExceptions.InvalidGroupException;
import io.github.labdev2025cmd.papinho.messaging.MessagingExceptions.InviteNotFoundException;
import io.github.labdev2025cmd.papinho.messaging.MessagingExceptions.NotFoundException;
import io.github.labdev2025cmd.papinho.messaging.MessagingExceptions.OnlyAdminsException;
import io.github.labdev2025cmd.papinho.privacy.PrivacyService;
import io.github.labdev2025cmd.papinho.profile.ProfileService;
import io.github.labdev2025cmd.papinho.profile.ProfileView;
import io.github.labdev2025cmd.papinho.shared.security.RateLimiter;

/**
 * Grupos (GR-01): criar, dados e foto, permissões, membros, admins e sair silenciosamente; convite por link com
 * aprovação de entrada opcional (GR-02). Cada mudança visível vira um aviso na conversa.
 */
@Service
public class GroupService {

    /** Limite de membros por grupo, como o do WhatsApp. */
    static final int MAX_MEMBERS = 1024;

    private final ConversationRepository conversations;
    private final ConversationMemberRepository members;
    private final GroupDetailsRepository groups;
    private final ConversationService conversationService;
    private final MessageService messages;
    private final MessageRepository messageRepository;
    private final SequenceAllocator sequences;
    private final UserDirectory users;
    private final ProfileService profiles;
    private final ContactQueries contacts;
    private final MediaService media;
    private final MessagingPreferences preferences;
    private final PrivacyService privacy;
    private final RateLimiter rateLimiter;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    GroupService(ConversationRepository conversations, ConversationMemberRepository members,
            GroupDetailsRepository groups, ConversationService conversationService, MessageService messages,
            MessageRepository messageRepository, SequenceAllocator sequences, UserDirectory users,
            ProfileService profiles, ContactQueries contacts, MediaService media, MessagingPreferences preferences,
            PrivacyService privacy, RateLimiter rateLimiter, ApplicationEventPublisher events, Clock clock) {
        this.conversations = conversations;
        this.members = members;
        this.groups = groups;
        this.conversationService = conversationService;
        this.messages = messages;
        this.messageRepository = messageRepository;
        this.sequences = sequences;
        this.users = users;
        this.profiles = profiles;
        this.contacts = contacts;
        this.media = media;
        this.preferences = preferences;
        this.privacy = privacy;
        this.rateLimiter = rateLimiter;
        this.events = events;
        this.clock = clock;
    }

    /** Cria o grupo com quem o criou como admin e os contatos escolhidos como membros. */
    @Transactional
    public ConversationService.ConversationView create(UUID me, String name, @Nullable String description,
            List<UUID> memberIds) {
        String cleanName = validName(name);
        Set<UUID> targets = new LinkedHashSet<>(memberIds);
        targets.remove(me);
        if (targets.size() + 1 > MAX_MEMBERS) {
            throw new InvalidGroupException("too many members");
        }
        targets.forEach(target -> requireAddable(me, target));
        Instant now = clock.instant();
        Conversation group = Conversation.group(me, now);
        group.changeDisappearing(preferences.defaultDisappearing(me));
        Conversation conversation = conversations.saveAndFlush(group);
        UUID id = conversation.id();
        groups.saveAndFlush(new GroupDetails(id, cleanName, validDescription(description), now));
        members.save(new ConversationMember(id, me, MemberState.ACTIVE, MemberRole.ADMIN, now));
        targets.forEach(target -> members.save(new ConversationMember(id, target, MemberState.ACTIVE, now)));
        members.flush();
        messages.appendSystem(id, me, notice("group.created", me, List.of(), cleanName));
        if (!targets.isEmpty()) {
            messages.appendSystem(id, me, notice("group.added", me, targets, null));
        }
        notifyMembers(id, Set.of());
        return conversationService.view(me, id);
    }

    /** Muda nome e descrição; quando "só admins editam os dados", só admins (GR-01). */
    @Transactional
    public ConversationService.ConversationView updateInfo(UUID me, UUID groupId, @Nullable String name,
            @Nullable String description) {
        GroupContext context = requireActive(me, groupId);
        requireCanEdit(context);
        Instant now = clock.instant();
        sequences.lock(groupId);
        if (name != null && !validName(name).equals(context.group().name())) {
            context.group().rename(validName(name), now);
            messages.appendSystem(groupId, me, notice("group.renamed", me, List.of(), context.group().name()));
        }
        if (description != null && !validDescription(description).equals(context.group().description())) {
            context.group().describe(validDescription(description), now);
            messages.appendSystem(groupId, me, notice("group.description", me, List.of(), null));
        }
        notifyMembers(groupId, Set.of());
        return conversationService.view(me, groupId);
    }

    /** Troca a foto por uma imagem já enviada por quem pede (upload tus), ou remove com {@code null}. */
    @Transactional
    public ConversationService.ConversationView changePhoto(UUID me, UUID groupId, @Nullable UUID mediaId) {
        GroupContext context = requireActive(me, groupId);
        requireCanEdit(context);
        if (mediaId != null) {
            MediaView photo = media.requireOwned(me, mediaId);
            if (photo.kind() != MediaKind.IMAGE || !photo.hasThumbnail()) {
                throw new InvalidGroupException("photo must be an image");
            }
        }
        UUID previous = context.group().photoMediaId();
        sequences.lock(groupId);
        context.group().changePhoto(mediaId, clock.instant());
        groups.flush();
        if (previous != null && !previous.equals(mediaId) && !groups.existsByPhotoMediaId(previous)
                && !messageRepository.existsByAttachmentId(previous)) {
            media.delete(previous);
        }
        messages.appendSystem(groupId, me, notice("group.photo", me, List.of(), mediaId == null ? "removed" : null));
        notifyMembers(groupId, Set.of());
        return conversationService.view(me, groupId);
    }

    /** Permissões do grupo e aprovação de entrada; só admins. */
    @Transactional
    public ConversationService.ConversationView changeSettings(UUID me, UUID groupId, boolean onlyAdminsSend,
            boolean onlyAdminsEditInfo, boolean onlyAdminsAddMembers, boolean joinApproval) {
        GroupContext context = requireAdmin(me, groupId);
        GroupDetails group = context.group();
        boolean changed = group.onlyAdminsSend() != onlyAdminsSend || group.onlyAdminsEditInfo() != onlyAdminsEditInfo
                || group.onlyAdminsAddMembers() != onlyAdminsAddMembers || group.joinApproval() != joinApproval;
        if (changed) {
            sequences.lock(groupId);
            group.changeSettings(onlyAdminsSend, onlyAdminsEditInfo, onlyAdminsAddMembers, joinApproval,
                    clock.instant());
            messages.appendSystem(groupId, me, notice("group.settings", me, List.of(), null));
            notifyMembers(groupId, Set.of());
        }
        return conversationService.view(me, groupId);
    }

    /** Membros ativos, admins primeiro, com o nome como {@code me} vê cada um. */
    @Transactional(readOnly = true)
    public List<MemberView> members(UUID me, UUID groupId) {
        requireActive(me, groupId);
        return toMemberViews(me, members.findByConversation(groupId).stream()
                .filter(m -> m.state() == MemberState.ACTIVE).toList());
    }

    /** Adiciona contatos ao grupo (CT-03: nunca quem bloqueou ou foi bloqueado). */
    @Transactional
    public List<MemberView> addMembers(UUID me, UUID groupId, List<UUID> userIds) {
        GroupContext context = requireActive(me, groupId);
        if (context.group().onlyAdminsAddMembers() && !context.member().admin()) {
            throw new OnlyAdminsException();
        }
        Map<UUID, ConversationMember> current = members.findByConversation(groupId).stream()
                .collect(Collectors.toMap(ConversationMember::userId, Function.identity()));
        Set<UUID> added = new LinkedHashSet<>();
        for (UUID target : new LinkedHashSet<>(userIds)) {
            ConversationMember existing = current.get(target);
            if (existing != null && existing.state() == MemberState.ACTIVE) {
                continue;
            }
            requireAddable(me, target);
            added.add(target);
        }
        long active = current.values().stream().filter(m -> m.state() == MemberState.ACTIVE).count();
        if (active + added.size() > MAX_MEMBERS) {
            throw new InvalidGroupException("too many members");
        }
        if (!added.isEmpty()) {
            sequences.lock(groupId);
            admit(groupId, added, current);
            messages.appendSystem(groupId, me, notice("group.added", me, added, null));
            notifyMembers(groupId, Set.of());
        }
        return members(me, groupId);
    }

    /** Remove alguém do grupo; só admins. */
    @Transactional
    public void removeMember(UUID me, UUID groupId, UUID userId) {
        requireAdmin(me, groupId);
        if (userId.equals(me)) {
            throw new InvalidGroupException("use leave to exit");
        }
        ConversationMember target = activeMember(groupId, userId);
        sequences.lock(groupId);
        messages.appendSystem(groupId, me, notice("group.removed", me, List.of(userId), null));
        target.changeState(MemberState.LEFT);
        target.changeRole(MemberRole.MEMBER);
        members.flush();
        ensureAdmin(groupId);
        notifyMembers(groupId, Set.of(userId));
    }

    /** Promove ou rebaixa um admin; só admins. */
    @Transactional
    public List<MemberView> setAdmin(UUID me, UUID groupId, UUID userId, boolean admin) {
        requireAdmin(me, groupId);
        ConversationMember target = activeMember(groupId, userId);
        if (target.admin() != admin) {
            sequences.lock(groupId);
            target.changeRole(admin ? MemberRole.ADMIN : MemberRole.MEMBER);
            members.flush();
            ensureAdmin(groupId);
            messages.appendSystem(groupId, me,
                    notice(admin ? "group.admin" : "group.notAdmin", me, List.of(userId), null));
            notifyMembers(groupId, Set.of());
        }
        return members(me, groupId);
    }

    /**
     * Sai silenciosamente (GR-01): nenhum aviso na conversa; a lista de membros é que muda. Se era o último admin,
     * o membro mais antigo vira admin.
     */
    @Transactional
    public void leave(UUID me, UUID groupId) {
        GroupContext context = requireActive(me, groupId);
        sequences.lock(groupId);
        context.member().changeState(MemberState.LEFT);
        context.member().changeRole(MemberRole.MEMBER);
        members.flush();
        ensureAdmin(groupId);
        notifyMembers(groupId, Set.of(me));
    }

    /**
     * A conta vai ser apagada (BK-02): sai de todos os grupos como no "sair silenciosamente", passando o admin
     * adiante quando era a única. A cascata do banco apaga a participação logo depois.
     */
    @org.springframework.context.event.EventListener
    @Transactional
    void on(io.github.labdev2025cmd.papinho.identity.AccountDeleting event) {
        for (ConversationMember member : members.findActiveByUser(event.userId())) {
            UUID groupId = member.conversationId();
            boolean group = conversations.findById(groupId).map(c -> c.kind() == ConversationKind.GROUP)
                    .orElse(false);
            if (!group) {
                continue;
            }
            sequences.lock(groupId);
            member.changeState(MemberState.LEFT);
            member.changeRole(MemberRole.MEMBER);
            members.flush();
            ensureAdmin(groupId);
            notifyMembers(groupId, Set.of());
        }
    }

    /** Link de convite atual (criado na primeira vez); só admins (GR-02). */
    @Transactional
    public String inviteCode(UUID me, UUID groupId) {
        GroupDetails group = requireAdmin(me, groupId).group();
        if (group.inviteCode() == null) {
            group.changeInviteCode(newCode(), clock.instant());
        }
        return group.inviteCode();
    }

    /** Gera um link novo; o anterior deixa de funcionar. */
    @Transactional
    public String resetInvite(UUID me, UUID groupId) {
        GroupDetails group = requireAdmin(me, groupId).group();
        group.changeInviteCode(newCode(), clock.instant());
        return group.inviteCode();
    }

    @Transactional
    public void revokeInvite(UUID me, UUID groupId) {
        requireAdmin(me, groupId).group().changeInviteCode(null, clock.instant());
    }

    /** Prévia do grupo pelo link, antes de entrar (GR-02). */
    @Transactional(readOnly = true)
    public InvitePreview preview(UUID me, String code) {
        rateLimiter.consume(RateLimiter.Policy.GROUP_INVITE_BY_USER, me.toString());
        GroupDetails group = groups.findByInviteCode(code).orElseThrow(InviteNotFoundException::new);
        List<ConversationMember> all = members.findByConversation(group.conversationId());
        long active = all.stream().filter(m -> m.state() == MemberState.ACTIVE).count();
        MemberState mine = all.stream().filter(m -> m.userId().equals(me)).map(ConversationMember::state)
                .findFirst().orElse(null);
        return new InvitePreview(group.conversationId(), group.name(), group.description(), active,
                group.joinApproval(), mine == MemberState.ACTIVE, mine == MemberState.PENDING);
    }

    /**
     * Entra pelo link. Com aprovação ligada vira um pedido pendente para os admins; senão entra na hora. Quem foi
     * bloqueado por um admin não entra (mesma resposta de link inválido).
     */
    @Transactional
    public JoinResult join(UUID me, String code) {
        rateLimiter.consume(RateLimiter.Policy.GROUP_INVITE_BY_USER, me.toString());
        GroupDetails group = groups.findByInviteCode(code).orElseThrow(InviteNotFoundException::new);
        UUID groupId = group.conversationId();
        sequences.lock(groupId);
        Map<UUID, ConversationMember> current = members.findByConversation(groupId).stream()
                .collect(Collectors.toMap(ConversationMember::userId, Function.identity()));
        ConversationMember existing = current.get(me);
        if (existing != null && existing.state() == MemberState.ACTIVE) {
            return new JoinResult(groupId, false);
        }
        boolean blocked = current.values().stream().filter(ConversationMember::admin)
                .anyMatch(admin -> contacts.isBlockedEitherWay(admin.userId(), me));
        if (blocked) {
            throw new InviteNotFoundException();
        }
        if (current.values().stream().filter(m -> m.state() == MemberState.ACTIVE).count() + 1 > MAX_MEMBERS) {
            throw new InvalidGroupException("too many members");
        }
        if (group.joinApproval()) {
            if (existing == null) {
                members.save(new ConversationMember(groupId, me, MemberState.PENDING, clock.instant()));
            } else {
                existing.changeState(MemberState.PENDING);
            }
            members.flush();
            events.publishEvent(new MessagingEvents.ConversationUpdated(groupId, admins(groupId)));
            return new JoinResult(groupId, true);
        }
        admit(groupId, Set.of(me), current);
        messages.appendSystem(groupId, me, notice("group.joined", me, List.of(), null));
        notifyMembers(groupId, Set.of());
        return new JoinResult(groupId, false);
    }

    /** Pedidos de entrada pendentes; só admins (GR-02). */
    @Transactional(readOnly = true)
    public List<MemberView> joinRequests(UUID me, UUID groupId) {
        requireAdmin(me, groupId);
        return toMemberViews(me, members.findByConversation(groupId).stream()
                .filter(m -> m.state() == MemberState.PENDING).toList());
    }

    /** Aprova ou recusa um pedido de entrada; só admins. */
    @Transactional
    public void decideJoin(UUID me, UUID groupId, UUID userId, boolean approve) {
        requireAdmin(me, groupId);
        ConversationMember request = members.findById(new ConversationMember.Key(groupId, userId))
                .filter(m -> m.state() == MemberState.PENDING).orElseThrow(NotFoundException::new);
        sequences.lock(groupId);
        if (approve) {
            // O pedido pode ter ficado na fila enquanto o grupo enchia: o limite vale na aprovação também.
            if (members.findByConversation(groupId).stream().filter(m -> m.state() == MemberState.ACTIVE).count()
                    + 1 > MAX_MEMBERS) {
                throw new InvalidGroupException("too many members");
            }
            request.rejoin(sequences.current(groupId), clock.instant());
            members.flush();
            members.clearPins(groupId, List.of(userId));
            messages.appendSystem(groupId, me, notice("group.added", me, List.of(userId), null));
            notifyMembers(groupId, Set.of());
        } else {
            request.changeState(MemberState.LEFT);
            members.flush();
            events.publishEvent(new MessagingEvents.ConversationUpdated(groupId, admins(groupId)));
        }
    }

    private void admit(UUID groupId, Set<UUID> userIds, Map<UUID, ConversationMember> current) {
        long seq = sequences.current(groupId);
        Instant now = clock.instant();
        for (UUID userId : userIds) {
            ConversationMember existing = current.get(userId);
            if (existing == null) {
                ConversationMember created = new ConversationMember(groupId, userId, MemberState.ACTIVE, now);
                created.rejoin(seq, now);
                members.save(created);
            } else {
                existing.rejoin(seq, now);
            }
        }
        members.flush();
        members.clearPins(groupId, userIds);
    }

    /** Grupo sem admin ativo: o membro mais antigo vira admin (como no WhatsApp). */
    private void ensureAdmin(UUID groupId) {
        List<ConversationMember> active = members.findByConversation(groupId).stream()
                .filter(m -> m.state() == MemberState.ACTIVE).toList();
        if (!active.isEmpty() && active.stream().noneMatch(ConversationMember::admin)) {
            active.stream().min(Comparator.comparing(ConversationMember::joinedAt)).orElseThrow()
                    .changeRole(MemberRole.ADMIN);
            members.flush();
        }
    }

    /** Só contatos de quem adiciona, com conta ativa e sem bloqueio entre os dois (CT-03). */
    private void requireAddable(UUID me, UUID target) {
        UserSummary user = users.findById(target).filter(UserSummary::active).orElseThrow(CannotAddException::new);
        if (!contacts.isContact(me, user.id()) || contacts.isBlockedEitherWay(me, user.id())) {
            throw new CannotAddException();
        }
        if (!privacy.canAddToGroups(me, user.id())) {
            // PV-07: quem não deixa ser adicionado entra só pelo link de convite.
            throw new MessagingExceptions.GroupAddNotAllowedException();
        }
    }

    private GroupContext requireActive(UUID me, UUID groupId) {
        Conversation conversation = conversations.findById(groupId)
                .filter(c -> c.kind() == ConversationKind.GROUP).orElseThrow(NotFoundException::new);
        ConversationMember member = members.findById(new ConversationMember.Key(conversation.id(), me))
                .filter(m -> m.state() == MemberState.ACTIVE).orElseThrow(NotFoundException::new);
        GroupDetails group = groups.findById(groupId).orElseThrow(NotFoundException::new);
        return new GroupContext(group, member);
    }

    private GroupContext requireAdmin(UUID me, UUID groupId) {
        GroupContext context = requireActive(me, groupId);
        if (!context.member().admin()) {
            throw new OnlyAdminsException();
        }
        return context;
    }

    private static void requireCanEdit(GroupContext context) {
        if (context.group().onlyAdminsEditInfo() && !context.member().admin()) {
            throw new OnlyAdminsException();
        }
    }

    private ConversationMember activeMember(UUID groupId, UUID userId) {
        return members.findById(new ConversationMember.Key(groupId, userId))
                .filter(m -> m.state() == MemberState.ACTIVE).orElseThrow(NotFoundException::new);
    }

    private Set<UUID> admins(UUID groupId) {
        return members.findByConversation(groupId).stream()
                .filter(m -> m.state() == MemberState.ACTIVE && m.admin()).map(ConversationMember::userId)
                .collect(Collectors.toSet());
    }

    /** Avisa os membros ativos (e quem saiu ou foi removido) que a conversa mudou. */
    private void notifyMembers(UUID groupId, Set<UUID> alsoNotify) {
        Set<UUID> recipients = new HashSet<>(conversationService.audience(groupId));
        recipients.addAll(alsoNotify);
        events.publishEvent(new MessagingEvents.ConversationUpdated(groupId, recipients));
    }

    private SystemNotice notice(String type, UUID actor, java.util.Collection<UUID> targets, @Nullable String value) {
        Set<UUID> ids = new HashSet<>(targets);
        ids.add(actor);
        // Os nomes de exibição (não os apelidos de quem fez) ficam no aviso.
        Map<UUID, ProfileView> views = profiles.viewAll(actor, ids);
        Map<UUID, UserSummary> summaries = users.findAllById(ids).stream()
                .collect(Collectors.toMap(UserSummary::id, Function.identity()));
        Function<UUID, SystemNotice.Person> person = id -> new SystemNotice.Person(id,
                views.containsKey(id) ? views.get(id).displayName()
                        : summaries.containsKey(id) ? summaries.get(id).username() : "");
        return new SystemNotice(type, person.apply(actor), targets.stream().map(person).toList(), value);
    }

    /** Pessoas como {@code me} as vê, sem papel de grupo (destinatários de lista de transmissão). */
    List<MemberView> describe(UUID me, List<UUID> userIds) {
        Instant now = clock.instant();
        return toMemberViews(me, userIds.stream()
                .map(id -> new ConversationMember(new UUID(0, 0), id, MemberState.ACTIVE, now)).toList());
    }

    private List<MemberView> toMemberViews(UUID me, List<ConversationMember> list) {
        Set<UUID> ids = list.stream().map(ConversationMember::userId).collect(Collectors.toSet());
        Map<UUID, UserSummary> summaries = users.findAllById(ids).stream()
                .collect(Collectors.toMap(UserSummary::id, Function.identity()));
        Map<UUID, ProfileView> views = profiles.viewAll(me, ids);
        Map<UUID, @Nullable String> nicknames = contacts.contactNicknames(me, ids);
        List<MemberView> result = new ArrayList<>();
        for (ConversationMember member : list) {
            UserSummary user = summaries.get(member.userId());
            if (user == null) {
                continue;
            }
            ProfileView view = views.get(user.id());
            String displayName = view == null ? user.username() : view.displayName();
            String nickname = nicknames.get(user.id());
            result.add(new MemberView(user.id(), user.username(), displayName,
                    nickname != null ? nickname : displayName, view != null && view.hasPhoto(),
                    view == null ? Instant.EPOCH : view.updatedAt(), member.role(), nicknames.containsKey(user.id()),
                    user.id().equals(me)));
        }
        result.sort(Comparator.comparing((MemberView m) -> m.role() != MemberRole.ADMIN)
                .thenComparing(m -> !m.me()).thenComparing(MemberView::label, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private String newCode() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String validName(String name) {
        String trimmed = name.strip();
        if (trimmed.isEmpty() || trimmed.length() > GroupDetails.MAX_NAME) {
            throw new InvalidGroupException("name");
        }
        return trimmed;
    }

    private static String validDescription(@Nullable String description) {
        String trimmed = description == null ? "" : description.strip();
        if (trimmed.length() > GroupDetails.MAX_DESCRIPTION) {
            throw new InvalidGroupException("description");
        }
        return trimmed;
    }

    private record GroupContext(GroupDetails group, ConversationMember member) {
    }

    /**
     * Membro como {@code me} o vê.
     *
     * @param label apelido do contato ou nome de exibição
     * @param me sou eu
     */
    public record MemberView(UUID userId, String username, String displayName, String label, boolean hasPhoto,
            Instant photoVersion, MemberRole role, boolean contact, boolean me) {
    }

    /**
     * Grupo visto pelo link de convite.
     *
     * @param member já sou membro
     * @param pending já pedi para entrar e espero aprovação
     */
    public record InvitePreview(UUID groupId, String name, String description, long memberCount,
            boolean joinApproval, boolean member, boolean pending) {
    }

    /** @param pending entrou como pedido, à espera da aprovação de um admin */
    public record JoinResult(UUID groupId, boolean pending) {
    }
}
