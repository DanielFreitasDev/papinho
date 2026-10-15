package io.github.labdev2025cmd.papinho.contact;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.labdev2025cmd.papinho.profile.ProfileVisibilityPolicy;

/**
 * Consultas de contato e bloqueio para os outros módulos (mensagens, grupos, presença, privacidade).
 * Também implementa a política de perfil: quem foi bloqueado não vê a foto nem o recado de quem bloqueou.
 */
@Service
public class ContactQueries implements ProfileVisibilityPolicy {

    private final ContactRepository contacts;
    private final BlockRepository blocks;

    ContactQueries(ContactRepository contacts, BlockRepository blocks) {
        this.contacts = contacts;
        this.blocks = blocks;
    }

    /** {@code owner} tem {@code other} nos contatos? */
    @Transactional(readOnly = true)
    public boolean isContact(UUID owner, UUID other) {
        return contacts.existsById(new Contact.Key(owner, other));
    }

    /** {@code blocker} bloqueou {@code blocked}? */
    @Transactional(readOnly = true)
    public boolean hasBlocked(UUID blocker, UUID blocked) {
        return blocks.existsById(new Block.Key(blocker, blocked));
    }

    /** Há bloqueio em qualquer direção entre os dois? */
    @Transactional(readOnly = true)
    public boolean isBlockedEitherWay(UUID a, UUID b) {
        return hasBlocked(a, b) || hasBlocked(b, a);
    }

    @Override
    public boolean canSeePhoto(UUID viewer, UUID owner) {
        return !hasBlocked(owner, viewer);
    }

    @Override
    public boolean canSeeAbout(UUID viewer, UUID owner) {
        return !hasBlocked(owner, viewer);
    }
}
