package io.github.labdev2025cmd.papinho.messaging;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Mensagem de texto. {@code seq} é a posição na conversa; {@code changeSeq} é o seq da última alteração
 * (criação, reação, fixar), para o cliente convergir por {@code afterSeq} (ADR 0010).
 */
@Entity
@Table(name = "messages")
class Message {

    /** Limite do corpo, igual ao do WhatsApp e ao CHECK da tabela. */
    static final int MAX_BODY_LENGTH = 65_536;

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private @Nullable UUID id;

    @Column(nullable = false)
    private UUID conversationId;

    @Column(nullable = false)
    private long seq;

    @Column(nullable = false)
    private long changeSeq;

    private @Nullable UUID senderId;

    @Column(nullable = false)
    private UUID clientMessageId;

    @Column(nullable = false)
    private String body;

    private @Nullable UUID replyToId;

    @Column(nullable = false)
    private int forwardCount;

    private @Nullable UUID attachmentId;

    private @Nullable String attachmentKind;

    private @Nullable Instant pinnedAt;

    @Column(nullable = false)
    private Instant createdAt;

    protected Message() {
        this.conversationId = new UUID(0, 0);
        this.clientMessageId = new UUID(0, 0);
        this.body = "";
        this.createdAt = Instant.EPOCH;
    }

    Message(UUID conversationId, long seq, UUID senderId, UUID clientMessageId, String body,
            @Nullable UUID replyToId, int forwardCount, @Nullable Attachment attachment, Instant now) {
        this.conversationId = conversationId;
        this.seq = seq;
        this.changeSeq = seq;
        this.senderId = senderId;
        this.clientMessageId = clientMessageId;
        this.body = body;
        this.replyToId = replyToId;
        this.forwardCount = forwardCount;
        if (attachment != null) {
            this.attachmentId = attachment.id();
            this.attachmentKind = attachment.kind();
        }
        this.createdAt = now;
    }

    UUID id() {
        if (id == null) {
            throw new IllegalStateException("Message not persisted yet");
        }
        return id;
    }

    UUID conversationId() {
        return conversationId;
    }

    long seq() {
        return seq;
    }

    long changeSeq() {
        return changeSeq;
    }

    @Nullable UUID senderId() {
        return senderId;
    }

    UUID clientMessageId() {
        return clientMessageId;
    }

    String body() {
        return body;
    }

    @Nullable UUID replyToId() {
        return replyToId;
    }

    int forwardCount() {
        return forwardCount;
    }

    /** Anexo da mensagem (MD-01), se houver. */
    @Nullable Attachment attachment() {
        return attachmentId == null || attachmentKind == null ? null : new Attachment(attachmentId, attachmentKind);
    }

    @Nullable Instant pinnedAt() {
        return pinnedAt;
    }

    Instant createdAt() {
        return createdAt;
    }

    /** Registra uma alteração visível a todos (reação, fixar), com o seq novo da conversa. */
    void changed(long changeSeq) {
        this.changeSeq = changeSeq;
    }

    void pin(Instant now, long changeSeq) {
        this.pinnedAt = now;
        changed(changeSeq);
    }

    void unpin(long changeSeq) {
        this.pinnedAt = null;
        changed(changeSeq);
    }

    /** Referência ao arquivo do módulo de mídia e o tipo dele (IMAGE, VIDEO, AUDIO, DOCUMENT). */
    record Attachment(UUID id, String kind) {
    }
}
