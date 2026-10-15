package io.github.labdev2025cmd.papinho.identity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Código de recuperação de uso único (ID-05), guardado só como hash Argon2. */
@Entity
@Table(name = "recovery_codes")
class RecoveryCode {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private @Nullable UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String codeHash;

    private @Nullable Instant usedAt;

    protected RecoveryCode() {
        this.userId = new UUID(0, 0);
        this.codeHash = "";
    }

    RecoveryCode(UUID userId, String codeHash) {
        this.userId = userId;
        this.codeHash = codeHash;
    }

    String codeHash() {
        return codeHash;
    }

    boolean used() {
        return usedAt != null;
    }

    void markUsed(Instant now) {
        this.usedAt = now;
    }
}
