package io.github.labdev2025cmd.papinho.messaging;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageKind kind;

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

    private @Nullable Instant editedAt;

    private @Nullable Instant deletedAt;

    private @Nullable Instant expiresAt;

    @Column(nullable = false)
    private boolean viewOnce;

    @Column(nullable = false)
    private boolean silent;

    private @Nullable UUID stickerId;

    protected Message() {
        this.conversationId = new UUID(0, 0);
        this.kind = MessageKind.USER;
        this.clientMessageId = new UUID(0, 0);
        this.body = "";
        this.createdAt = Instant.EPOCH;
    }

    Message(UUID conversationId, long seq, UUID senderId, UUID clientMessageId, String body,
            @Nullable UUID replyToId, int forwardCount, @Nullable Attachment attachment, Instant now) {
        this(conversationId, seq, MessageKind.USER, senderId, clientMessageId, body, replyToId, forwardCount,
                attachment, now);
    }

    Message(UUID conversationId, long seq, MessageKind kind, UUID senderId, UUID clientMessageId, String body,
            @Nullable UUID replyToId, int forwardCount, @Nullable Attachment attachment, Instant now) {
        this.conversationId = conversationId;
        this.seq = seq;
        this.kind = kind;
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

    MessageKind kind() {
        return kind;
    }

    boolean system() {
        return kind == MessageKind.SYSTEM;
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

    @Nullable Instant editedAt() {
        return editedAt;
    }

    @Nullable Instant deletedAt() {
        return deletedAt;
    }

    /** Quando some (mensagem temporária, MS-07); nulo = não expira. */
    @Nullable Instant expiresAt() {
        return expiresAt;
    }

    void expireAt(@Nullable Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    /** Visualização única (MS-08): o anexo só abre por token, uma vez por destinatário. */
    boolean viewOnce() {
        return viewOnce;
    }

    void markViewOnce() {
        this.viewOnce = true;
    }

    /** Envio silencioso (MS-11): chega normalmente, mas sem notificação. */
    boolean silent() {
        return silent;
    }

    void markSilent() {
        this.silent = true;
    }

    /** Figurinha enviada (MS-10), se for uma mensagem de figurinha. */
    @Nullable UUID stickerId() {
        return stickerId;
    }

    void useSticker(@Nullable UUID stickerId) {
        this.stickerId = stickerId;
    }

    /** Tipo do anexo, que fica mesmo depois de o arquivo de visualização única sair do servidor. */
    @Nullable String attachmentKind() {
        return attachmentKind;
    }

    /** Tira o arquivo de visualização única (todos abriram ou passou o prazo); devolve o id dele. */
    @Nullable UUID purgeViewOnce(long changeSeq) {
        UUID removed = attachmentId;
        this.attachmentId = null;
        changed(changeSeq);
        return removed;
    }

    boolean deleted() {
        return deletedAt != null;
    }

    /** Troca o texto (MS-05); a legenda de um anexo também pode ser editada. */
    void edit(String body, Instant now, long changeSeq) {
        this.body = body;
        this.editedAt = now;
        changed(changeSeq);
    }

    /**
     * Apaga para todos (MS-06): some o texto, o anexo, a resposta e o selo de encaminhada; fica só o aviso. Devolve
     * o anexo que deixou de ser usado por esta mensagem, para o arquivo ser removido.
     */
    @Nullable Attachment deleteForEveryone(Instant now, long changeSeq) {
        Attachment removed = attachment();
        this.body = "";
        this.attachmentId = null;
        this.attachmentKind = null;
        this.replyToId = null;
        this.stickerId = null;
        this.forwardCount = 0;
        this.pinnedAt = null;
        this.editedAt = null;
        this.deletedAt = now;
        changed(changeSeq);
        return removed;
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
