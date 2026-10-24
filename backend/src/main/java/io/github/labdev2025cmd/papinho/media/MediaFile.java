package io.github.labdev2025cmd.papinho.media;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Arquivo aceito: metadados no banco, conteúdo na porta de armazenamento (ADR 0006). */
@Entity
@Table(name = "media_files")
class MediaFile {

    @Id
    private UUID id;

    private @Nullable UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MediaKind kind;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private long size;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false)
    private String sha256;

    private @Nullable Integer width;

    private @Nullable Integer height;

    private @Nullable String thumbhash;

    @Column(nullable = false)
    private boolean hasThumbnail;

    @Column(nullable = false)
    private Instant createdAt;

    /** Algum módulo usa o arquivo, conferido pela limpeza dos que nunca foram usados (MD-01). */
    @Column(nullable = false)
    private boolean inUse;

    /** Duração do áudio lida do arquivo (AU-02); nula quando o arquivo não diz. */
    private @Nullable Long durationMs;

    /** Áudio enviado antes de a duração ser lida no upload, à espera do job que a preenche. */
    @Column(nullable = false)
    private boolean durationPending;

    protected MediaFile() {
        this.id = new UUID(0, 0);
        this.kind = MediaKind.DOCUMENT;
        this.contentType = "";
        this.filename = "";
        this.sha256 = "";
        this.createdAt = Instant.EPOCH;
    }

    MediaFile(UUID id, UUID ownerId, MediaKind kind, String contentType, long size, String filename, String sha256,
            @Nullable Integer width, @Nullable Integer height, @Nullable String thumbhash, boolean hasThumbnail,
            @Nullable Long durationMs, Instant now) {
        this.id = id;
        this.ownerId = ownerId;
        this.kind = kind;
        this.contentType = contentType;
        this.size = size;
        this.filename = filename;
        this.sha256 = sha256;
        this.width = width;
        this.height = height;
        this.thumbhash = thumbhash;
        this.hasThumbnail = hasThumbnail;
        this.durationMs = durationMs;
        this.createdAt = now;
    }

    UUID id() {
        return id;
    }

    @Nullable UUID ownerId() {
        return ownerId;
    }

    MediaKind kind() {
        return kind;
    }

    String contentType() {
        return contentType;
    }

    String filename() {
        return filename;
    }

    boolean hasThumbnail() {
        return hasThumbnail;
    }

    static String contentKey(UUID id) {
        return "media/" + id;
    }

    static String thumbnailKey(UUID id) {
        return "media/" + id + "-thumb";
    }

    /** Duração lida pelo job dos áudios antigos; nula se o arquivo não disser (não volta a ser lido). */
    void durationRead(@Nullable Long millis) {
        this.durationMs = millis;
        this.durationPending = false;
    }

    MediaView toView() {
        return new MediaView(id, ownerId == null ? new UUID(0, 0) : ownerId, kind, contentType, size, filename, width,
                height, thumbhash, hasThumbnail, durationMs, createdAt);
    }
}
