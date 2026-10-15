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
 * Conversa. O contador {@code last_seq} é avançado só por {@link SequenceAllocator} (UPDATE ... RETURNING com
 * trava de linha), por isso não é gravado pela entidade.
 */
@Entity
@Table(name = "conversations")
class Conversation {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private @Nullable UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConversationKind kind;

    private @Nullable String directKey;

    private @Nullable UUID createdBy;

    @Column(nullable = false, insertable = false, updatable = false)
    private long lastSeq;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Conversation() {
        this.kind = ConversationKind.DIRECT;
        this.createdAt = Instant.EPOCH;
        this.updatedAt = Instant.EPOCH;
    }

    private Conversation(ConversationKind kind, @Nullable String directKey, UUID createdBy, Instant now) {
        this.kind = kind;
        this.directKey = directKey;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    static Conversation direct(UUID creator, UUID other, Instant now) {
        return new Conversation(ConversationKind.DIRECT, directKey(creator, other), creator, now);
    }

    static Conversation group(UUID creator, Instant now) {
        return new Conversation(ConversationKind.GROUP, null, creator, now);
    }

    /** "Mensagens salvas" (MS-12): uma por pessoa, pela chave única {@code self:<id>}. */
    static Conversation self(UUID owner, Instant now) {
        return new Conversation(ConversationKind.SELF, selfKey(owner), owner, now);
    }

    static String selfKey(UUID owner) {
        return "self:" + owner;
    }

    /** Chave única do par, independente de quem abriu a conversa. */
    static String directKey(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
    }

    UUID id() {
        if (id == null) {
            throw new IllegalStateException("Conversation not persisted yet");
        }
        return id;
    }

    ConversationKind kind() {
        return kind;
    }

    @Nullable UUID createdBy() {
        return createdBy;
    }

    void touch(Instant now) {
        this.updatedAt = now;
    }
}
