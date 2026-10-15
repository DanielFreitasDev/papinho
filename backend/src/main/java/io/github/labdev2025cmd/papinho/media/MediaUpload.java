package io.github.labdev2025cmd.papinho.media;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Upload tus em andamento (ADR 0009). O deslocamento atual é o tamanho do arquivo parcial em disco; a linha só
 * guarda o que foi declarado na criação.
 */
@Entity
@Table(name = "media_uploads")
class MediaUpload {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private long length;

    @Column(nullable = false)
    private String filename;

    private @Nullable String declaredType;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    protected MediaUpload() {
        this.id = new UUID(0, 0);
        this.ownerId = new UUID(0, 0);
        this.filename = "";
        this.createdAt = Instant.EPOCH;
        this.expiresAt = Instant.EPOCH;
    }

    MediaUpload(UUID id, UUID ownerId, long length, String filename, @Nullable String declaredType, Instant now,
            Instant expiresAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.length = length;
        this.filename = filename;
        this.declaredType = declaredType;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    UUID id() {
        return id;
    }

    UUID ownerId() {
        return ownerId;
    }

    long length() {
        return length;
    }

    String filename() {
        return filename;
    }

    @Nullable String declaredType() {
        return declaredType;
    }

    Instant expiresAt() {
        return expiresAt;
    }
}
