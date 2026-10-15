package io.github.labdev2025cmd.papinho.messaging;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

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

    @Column(nullable = false)
    private long deliveredSeq;

    @Column(nullable = false)
    private long readSeq;

    @Column(nullable = false)
    private boolean markedUnread;

    @Column(nullable = false)
    private Instant joinedAt;

    protected ConversationMember() {
        this.key = new Key(new UUID(0, 0), new UUID(0, 0));
        this.state = MemberState.ACTIVE;
        this.joinedAt = Instant.EPOCH;
    }

    ConversationMember(UUID conversationId, UUID userId, MemberState state, Instant now) {
        this.key = new Key(conversationId, userId);
        this.state = state;
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
