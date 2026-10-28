package io.github.labdev2025cmd.papinho.contact;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** {@code blocker} bloqueou {@code blocked} (CT-03). */
@Entity
@Table(name = "blocks")
class Block {

    @EmbeddedId
    private Key key;

    @Column(nullable = false)
    private Instant createdAt;

    protected Block() {
        this.key = new Key(new UUID(0, 0), new UUID(0, 0));
        this.createdAt = Instant.EPOCH;
    }

    Block(UUID blockerId, UUID blockedId, Instant now) {
        this.key = new Key(blockerId, blockedId);
        this.createdAt = now;
    }

    UUID blockedId() {
        return key.blockedId();
    }

    /** A outra pessoa do bloqueio, para {@code userId} (que bloqueou ou foi bloqueado). */
    UUID otherThan(UUID userId) {
        return key.blockerId().equals(userId) ? key.blockedId() : key.blockerId();
    }

    Instant createdAt() {
        return createdAt;
    }

    /** Chave composta (quem bloqueou, quem foi bloqueado). */
    @Embeddable
    record Key(@Column(name = "blocker_id") UUID blockerId, @Column(name = "blocked_id") UUID blockedId)
            implements Serializable {
    }
}
