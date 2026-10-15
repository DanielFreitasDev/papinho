package io.github.labdev2025cmd.papinho.contact;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.labdev2025cmd.papinho.identity.UserDirectory;
import io.github.labdev2025cmd.papinho.identity.UserDirectory.UserSummary;
import io.github.labdev2025cmd.papinho.profile.ProfileService;
import io.github.labdev2025cmd.papinho.profile.ProfileView;
import io.github.labdev2025cmd.papinho.shared.error.ApiException;

/** Casos de uso de contatos e bloqueios (CT-01, CT-03). */
@Service
public class ContactService {

    private final ContactRepository contacts;
    private final BlockRepository blocks;
    private final UserDirectory users;
    private final ProfileService profiles;
    private final Clock clock;

    ContactService(ContactRepository contacts, BlockRepository blocks, UserDirectory users, ProfileService profiles,
            Clock clock) {
        this.contacts = contacts;
        this.blocks = blocks;
        this.users = users;
        this.profiles = profiles;
        this.clock = clock;
    }

    /** Cartão público de alguém pelo {@code @usuario}, para a tela "adicionar contato" (link e QR Code). */
    @Transactional(readOnly = true)
    public PersonView lookup(UUID viewer, String username) {
        UserSummary user = users.findByUsername(username).orElseThrow(NotFoundException::new);
        return person(viewer, user);
    }

    /** Adiciona (ou atualiza o apelido de) um contato. Adicionar a si mesmo não é permitido. */
    @Transactional
    public ContactView add(UUID owner, String username, @Nullable String nickname) {
        UserSummary user = users.findByUsername(username).orElseThrow(NotFoundException::new);
        if (user.id().equals(owner)) {
            throw new SelfContactException();
        }
        Contact contact = contacts.findById(new Contact.Key(owner, user.id()))
                .orElseGet(() -> new Contact(owner, user.id(), null, clock.instant()));
        contact.rename(clean(nickname));
        contacts.save(contact);
        return toView(owner, contact, user);
    }

    @Transactional(readOnly = true)
    public List<ContactView> list(UUID owner) {
        List<Contact> found = contacts.findByOwner(owner);
        Map<UUID, UserSummary> summaries = users.findAllById(found.stream().map(Contact::contactId).toList())
                .stream().collect(java.util.stream.Collectors.toMap(UserSummary::id, s -> s));
        Map<UUID, ProfileView> views = profiles.viewAll(owner, summaries.keySet());
        return found.stream().filter(c -> summaries.containsKey(c.contactId()))
                .map(c -> toView(c, summaries.get(c.contactId()), views.get(c.contactId())))
                .sorted(java.util.Comparator.comparing(ContactView::label, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public ContactView rename(UUID owner, UUID contactId, @Nullable String nickname) {
        Contact contact = contacts.findById(new Contact.Key(owner, contactId)).orElseThrow(NotFoundException::new);
        contact.rename(clean(nickname));
        return toView(owner, contact, users.findById(contactId).orElseThrow(NotFoundException::new));
    }

    @Transactional
    public void remove(UUID owner, UUID contactId) {
        Contact.Key key = new Contact.Key(owner, contactId);
        if (!contacts.existsById(key)) {
            throw new NotFoundException();
        }
        contacts.deleteById(key);
    }

    /** Bloqueia; idempotente. O bloqueio não remove o contato, como no WhatsApp. */
    @Transactional
    public void block(UUID blocker, UUID blocked) {
        if (blocker.equals(blocked)) {
            throw new SelfContactException();
        }
        users.findById(blocked).orElseThrow(NotFoundException::new);
        if (!blocks.existsById(new Block.Key(blocker, blocked))) {
            blocks.save(new Block(blocker, blocked, clock.instant()));
        }
    }

    @Transactional
    public void unblock(UUID blocker, UUID blocked) {
        blocks.deleteById(new Block.Key(blocker, blocked));
    }

    @Transactional(readOnly = true)
    public List<BlockedView> blocked(UUID blocker) {
        List<Block> found = blocks.findByBlocker(blocker);
        Map<UUID, ProfileView> views = profiles.viewAll(blocker, found.stream().map(Block::blockedId).toList());
        Map<UUID, UserSummary> summaries = users.findAllById(found.stream().map(Block::blockedId).toList()).stream()
                .collect(java.util.stream.Collectors.toMap(UserSummary::id, s -> s));
        return found.stream().filter(b -> summaries.containsKey(b.blockedId()))
                .map(b -> new BlockedView(b.blockedId(), summaries.get(b.blockedId()).username(),
                        views.containsKey(b.blockedId()) ? views.get(b.blockedId()).displayName() : "",
                        b.createdAt()))
                .toList();
    }

    private PersonView person(UUID viewer, UserSummary user) {
        ProfileView view = profiles.view(viewer, user.id()).orElseThrow(NotFoundException::new);
        boolean contact = contacts.existsById(new Contact.Key(viewer, user.id()));
        boolean blocked = blocks.existsById(new Block.Key(viewer, user.id()));
        return new PersonView(user.id(), user.username(), view.displayName(), view.about(), view.hasPhoto(),
                view.updatedAt(), contact, blocked, viewer.equals(user.id()));
    }

    private ContactView toView(UUID owner, Contact contact, UserSummary user) {
        return toView(contact, user, profiles.view(owner, user.id()).orElseThrow(NotFoundException::new));
    }

    private static ContactView toView(Contact contact, UserSummary user, @Nullable ProfileView view) {
        String displayName = view == null ? user.username() : view.displayName();
        String nickname = contact.nickname();
        return new ContactView(user.id(), user.username(), displayName, nickname,
                nickname != null ? nickname : displayName, view != null && view.hasPhoto(),
                view == null ? Instant.EPOCH : view.updatedAt(), view == null ? null : view.about());
    }

    private static @Nullable String clean(@Nullable String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return null;
        }
        String value = nickname.strip();
        return value.length() > 64 ? value.substring(0, 64) : value;
    }

    /**
     * Contato na lista do dono.
     *
     * @param label nome mostrado: o apelido local, se houver, senão o nome de exibição
     * @param photoVersion muda quando a foto muda (parâmetro de cache da URL)
     */
    public record ContactView(UUID userId, String username, String displayName, @Nullable String nickname,
            String label, boolean hasPhoto, Instant photoVersion, @Nullable String about) {
    }

    /** Cartão de uma pessoa visto por alguém. */
    public record PersonView(UUID userId, String username, String displayName, @Nullable String about,
            boolean hasPhoto, Instant photoVersion, boolean contact, boolean blocked, boolean self) {
    }

    /** Pessoa na lista de bloqueados. */
    public record BlockedView(UUID userId, String username, String displayName, Instant blockedAt) {
    }

    static class NotFoundException extends ApiException {
        NotFoundException() {
            super(HttpStatus.NOT_FOUND, "not-found", "User or contact not found");
        }
    }

    static class SelfContactException extends ApiException {
        SelfContactException() {
            super(HttpStatus.BAD_REQUEST, "contact-self", "Cannot add or block yourself");
        }
    }
}
