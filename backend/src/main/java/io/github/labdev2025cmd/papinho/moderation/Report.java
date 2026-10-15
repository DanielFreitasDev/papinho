package io.github.labdev2025cmd.papinho.moderation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

/** Denúncia de um usuário, com a cópia das últimas mensagens da conversa (CT-04). */
@Entity
@Table(name = "reports")
class Report {

    enum Status {
        OPEN, RESOLVED, DISMISSED
    }

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private @Nullable UUID id;

    private @Nullable UUID reporterId;

    @Column(nullable = false)
    private UUID reportedId;

    private @Nullable UUID conversationId;

    private @Nullable String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private Instant createdAt;

    private @Nullable Instant resolvedAt;

    private @Nullable UUID resolvedBy;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "report_messages", joinColumns = @JoinColumn(name = "report_id"))
    @OrderColumn(name = "position")
    private List<Copy> messages = new ArrayList<>();

    protected Report() {
        this.reportedId = new UUID(0, 0);
        this.status = Status.OPEN;
        this.createdAt = Instant.EPOCH;
    }

    Report(UUID reporterId, UUID reportedId, @Nullable UUID conversationId, @Nullable String reason,
            List<Copy> messages, Instant now) {
        this.reporterId = reporterId;
        this.reportedId = reportedId;
        this.conversationId = conversationId;
        this.reason = reason;
        this.status = Status.OPEN;
        this.messages = new ArrayList<>(messages);
        this.createdAt = now;
    }

    UUID id() {
        if (id == null) {
            throw new IllegalStateException("Report not persisted yet");
        }
        return id;
    }

    @Nullable UUID reporterId() {
        return reporterId;
    }

    UUID reportedId() {
        return reportedId;
    }

    @Nullable String reason() {
        return reason;
    }

    Status status() {
        return status;
    }

    Instant createdAt() {
        return createdAt;
    }

    @Nullable Instant resolvedAt() {
        return resolvedAt;
    }

    List<Copy> messages() {
        return List.copyOf(messages);
    }

    /** Fecha a denúncia (resolvida ou arquivada) e registra quem fechou. */
    void close(Status outcome, UUID adminId, Instant now) {
        this.status = outcome;
        this.resolvedBy = adminId;
        this.resolvedAt = now;
    }

    /** Mensagem copiada no momento da denúncia. */
    @Embeddable
    record Copy(@Column(name = "message_id", nullable = false) UUID messageId,
            @Column(name = "sender_id") @Nullable UUID senderId, @Column(name = "body", nullable = false) String body,
            @Column(name = "sent_at", nullable = false) Instant sentAt) {
    }
}
