package io.github.labdev2025cmd.papinho.identity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Convite para criar conta (ID-07), compartilhado por link ou QR Code, com expiração e limite de usos.
 * O token aparece só uma vez para quem criou; o banco guarda o SHA-256 dele.
 */
@Entity
@Table(name = "invites")
class Invite {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private @Nullable UUID id;

    @Column(nullable = false, unique = true)
    private String tokenHash;

    @Column(nullable = false)
    private UUID createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int maxUses;

    @Column(nullable = false)
    private int uses;

    private @Nullable Instant revokedAt;

    protected Invite() {
        this.tokenHash = "";
        this.createdBy = new UUID(0, 0);
        this.createdAt = Instant.EPOCH;
        this.expiresAt = Instant.EPOCH;
    }

    Invite(String tokenHash, UUID createdBy, Instant now, Instant expiresAt, int maxUses) {
        this.tokenHash = tokenHash;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.expiresAt = expiresAt;
        this.maxUses = maxUses;
    }

    UUID id() {
        if (id == null) {
            throw new IllegalStateException("Invite not persisted yet");
        }
        return id;
    }

    UUID createdBy() {
        return createdBy;
    }

    Instant createdAt() {
        return createdAt;
    }

    Instant expiresAt() {
        return expiresAt;
    }

    int maxUses() {
        return maxUses;
    }

    int uses() {
        return uses;
    }

    boolean revoked() {
        return revokedAt != null;
    }

    boolean usable(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now) && uses < maxUses;
    }

    void consume(Instant now) {
        if (!usable(now)) {
            throw new IllegalStateException("Invite is not usable");
        }
        uses++;
    }

    void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }
}
