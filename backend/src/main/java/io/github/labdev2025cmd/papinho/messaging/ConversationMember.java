package io.github.labdev2025cmd.papinho.messaging;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Participação de um usuário numa conversa, com os ponteiros de entrega e leitura (MS-02, MS-14). */
@Entity
@Table(name = "conversation_members")
class ConversationMember {

    @EmbeddedId
    private Key key;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberState state;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberRole role;

    @Column(nullable = false)
    private long deliveredSeq;

    @Column(nullable = false)
    private long readSeq;

    @Column(nullable = false)
    private boolean markedUnread;

    @Column(nullable = false)
    private Instant joinedAt;

    /** Confirmação de leitura nesta conversa (PV-06); nulo segue o padrão (global na individual, ligada no grupo). */
    private @Nullable Boolean readReceipts;

    protected ConversationMember() {
        this.key = new Key(new UUID(0, 0), new UUID(0, 0));
        this.state = MemberState.ACTIVE;
        this.role = MemberRole.MEMBER;
        this.joinedAt = Instant.EPOCH;
    }

    ConversationMember(UUID conversationId, UUID userId, MemberState state, Instant now) {
        this(conversationId, userId, state, MemberRole.MEMBER, now);
    }

    ConversationMember(UUID conversationId, UUID userId, MemberState state, MemberRole role, Instant now) {
        this.key = new Key(conversationId, userId);
        this.state = state;
        this.role = role;
        this.joinedAt = now;
    }

    UUID conversationId() {
        return key.conversationId();
    }

    UUID userId() {
        return key.userId();
    }

    MemberState state() {
        return state;
    }

    MemberRole role() {
        return role;
    }

    boolean admin() {
        return role == MemberRole.ADMIN;
    }

    @Nullable Boolean readReceipts() {
        return readReceipts;
    }

    void changeReadReceipts(@Nullable Boolean enabled) {
        this.readReceipts = enabled;
    }

    Instant joinedAt() {
        return joinedAt;
    }

    void changeRole(MemberRole role) {
        this.role = role;
    }

    /**
     * Volta (ou entra) no grupo: os ponteiros começam no seq atual, para o histórico de antes não contar como não
     * lido.
     */
    void rejoin(long currentSeq, Instant now) {
        this.state = MemberState.ACTIVE;
        this.role = MemberRole.MEMBER;
        this.joinedAt = now;
        this.deliveredSeq = Math.max(deliveredSeq, currentSeq);
        this.readSeq = Math.max(readSeq, currentSeq);
        this.markedUnread = false;
    }

    long deliveredSeq() {
        return deliveredSeq;
    }

    long readSeq() {
        return readSeq;
    }

    boolean markedUnread() {
        return markedUnread;
    }

    void changeState(MemberState state) {
        this.state = state;
    }

    /** Avança o ponteiro de entrega; nunca recua. Devolve se mudou. */
    boolean advanceDelivered(long seq) {
        if (seq <= deliveredSeq) {
            return false;
        }
        deliveredSeq = seq;
        return true;
    }

    /** Avança o ponteiro de leitura (que implica entrega) e limpa "marcar como não lida". Devolve se mudou. */
    boolean advanceRead(long seq) {
        markedUnread = false;
        advanceDelivered(seq);
        if (seq <= readSeq) {
            return false;
        }
        readSeq = seq;
        return true;
    }

    void markUnread() {
        this.markedUnread = true;
    }

    /** Chave composta (conversa, usuário). */
    @Embeddable
    record Key(@Column(name = "conversation_id") UUID conversationId, @Column(name = "user_id") UUID userId)
            implements Serializable {
    }
}
