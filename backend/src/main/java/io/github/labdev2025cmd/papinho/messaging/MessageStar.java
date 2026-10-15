package io.github.labdev2025cmd.papinho.messaging;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Mensagem favoritada (estrelada) por um usuário; visível só para ele (MS-04). */
@Entity
@Table(name = "message_stars")
class MessageStar {

    @EmbeddedId
    private Key key;

    @Column(nullable = false)
    private Instant createdAt;

    protected MessageStar() {
        this.key = new Key(new UUID(0, 0), new UUID(0, 0));
        this.createdAt = Instant.EPOCH;
    }

    MessageStar(UUID userId, UUID messageId, Instant now) {
        this.key = new Key(userId, messageId);
        this.createdAt = now;
    }

    UUID messageId() {
        return key.messageId();
    }

    /** Chave composta (usuário, mensagem). */
    @Embeddable
    record Key(@Column(name = "user_id") UUID userId, @Column(name = "message_id") UUID messageId)
            implements Serializable {
    }
}
