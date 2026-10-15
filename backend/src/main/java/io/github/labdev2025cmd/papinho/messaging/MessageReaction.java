package io.github.labdev2025cmd.papinho.messaging;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Reação de um usuário a uma mensagem; uma por pessoa, como no WhatsApp (MS-04). */
@Entity
@Table(name = "message_reactions")
class MessageReaction {

    @EmbeddedId
    private Key key;

    @Column(nullable = false)
    private String emoji;

    @Column(nullable = false)
    private Instant createdAt;

    protected MessageReaction() {
        this.key = new Key(new UUID(0, 0), new UUID(0, 0));
        this.emoji = "";
        this.createdAt = Instant.EPOCH;
    }

    MessageReaction(UUID messageId, UUID userId, String emoji, Instant now) {
        this.key = new Key(messageId, userId);
        this.emoji = emoji;
        this.createdAt = now;
    }

    UUID messageId() {
        return key.messageId();
    }

    UUID userId() {
        return key.userId();
    }

    String emoji() {
        return emoji;
    }

    void change(String emoji, Instant now) {
        this.emoji = emoji;
        this.createdAt = now;
    }

    /** Chave composta (mensagem, usuário). */
    @Embeddable
    record Key(@Column(name = "message_id") UUID messageId, @Column(name = "user_id") UUID userId)
            implements Serializable {
    }
}
