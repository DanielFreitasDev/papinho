package io.github.labdev2025cmd.papinho.transcription;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Provedor de transcrição configurado pelo admin; a chave fica cifrada e nunca sai do servidor. */
@Entity
@Table(name = "transcription_providers")
class TranscriptionProvider {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProviderKind kind;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String baseUrl;

    @Column(nullable = false)
    private String model;

    private @Nullable String apiKeyEncrypted;

    private @Nullable String hotwords;

    @Column(nullable = false)
    private long maxBytes;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private Instant createdAt;

    protected TranscriptionProvider() {
        this.id = new UUID(0, 0);
        this.kind = ProviderKind.CUSTOM;
        this.name = "";
        this.baseUrl = "";
        this.model = "";
        this.createdAt = Instant.EPOCH;
    }

    TranscriptionProvider(UUID id, ProviderKind kind, Instant now) {
        this.id = id;
        this.kind = kind;
        this.name = kind.name();
        this.baseUrl = kind.defaultBaseUrl();
        this.model = kind.defaultModel();
        this.maxBytes = kind.defaultMaxBytes();
        this.enabled = true;
        this.createdAt = now;
    }

    void update(String name, String baseUrl, String model, @Nullable String hotwords, long maxBytes,
            boolean enabled) {
        this.name = name;
        this.baseUrl = baseUrl;
        this.model = model;
        this.hotwords = hotwords;
        this.maxBytes = maxBytes;
        this.enabled = enabled;
    }

    void apiKeyEncrypted(@Nullable String value) {
        this.apiKeyEncrypted = value;
    }

    UUID id() {
        return id;
    }

    ProviderKind kind() {
        return kind;
    }

    String name() {
        return name;
    }

    String baseUrl() {
        return baseUrl;
    }

    String model() {
        return model;
    }

    @Nullable String apiKeyEncrypted() {
        return apiKeyEncrypted;
    }

    @Nullable String hotwords() {
        return hotwords;
    }

    long maxBytes() {
        return maxBytes;
    }

    boolean enabled() {
        return enabled;
    }

    Instant createdAt() {
        return createdAt;
    }
}
