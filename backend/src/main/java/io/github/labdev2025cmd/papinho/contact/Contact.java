package io.github.labdev2025cmd.papinho.contact;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Contato salvo por um usuário, com apelido visível só para ele. */
@Entity
@Table(name = "contacts")
class Contact {

    @EmbeddedId
    private Key key;

    @Column(length = 64)
    private @Nullable String nickname;

    @Column(nullable = false)
    private Instant createdAt;

    protected Contact() {
        this.key = new Key(new UUID(0, 0), new UUID(0, 0));
        this.createdAt = Instant.EPOCH;
    }

    Contact(UUID ownerId, UUID contactId, @Nullable String nickname, Instant now) {
        this.key = new Key(ownerId, contactId);
        this.nickname = nickname;
        this.createdAt = now;
    }

    UUID contactId() {
        return key.contactId();
    }

    @Nullable String nickname() {
        return nickname;
    }

    Instant createdAt() {
        return createdAt;
    }

    void rename(@Nullable String nickname) {
        this.nickname = nickname;
    }

    /** Chave composta (dono, contato). */
    @Embeddable
    record Key(@Column(name = "owner_id") UUID ownerId, @Column(name = "contact_id") UUID contactId)
            implements Serializable {
    }
}
