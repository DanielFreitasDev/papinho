package io.github.labdev2025cmd.papinho.profile;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Perfil de um usuário; o id é o mesmo da conta. */
@Entity
@Table(name = "profiles")
class Profile {

    static final int DISPLAY_NAME_MAX = 64;
    static final int ABOUT_MAX = 140;

    @Id
    private UUID userId;

    @Column(nullable = false, length = DISPLAY_NAME_MAX)
    private String displayName;

    @Column(length = ABOUT_MAX)
    private @Nullable String about;

    /** Chave do arquivo da foto grande no armazenamento (ADR 0006); a miniatura usa o sufixo {@code -thumb}. */
    private @Nullable String photoKey;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Profile() {
        this.userId = new UUID(0, 0);
        this.displayName = "";
        this.updatedAt = Instant.EPOCH;
    }

    Profile(UUID userId, String displayName, Instant now) {
        this.userId = userId;
        this.displayName = displayName;
        this.updatedAt = now;
    }

    UUID userId() {
        return userId;
    }

    String displayName() {
        return displayName;
    }

    @Nullable String about() {
        return about;
    }

    @Nullable String photoKey() {
        return photoKey;
    }

    Instant updatedAt() {
        return updatedAt;
    }

    void update(String displayName, @Nullable String about, Instant now) {
        this.displayName = displayName;
        this.about = about;
        this.updatedAt = now;
    }

    void changePhoto(@Nullable String photoKey, Instant now) {
        this.photoKey = photoKey;
        this.updatedAt = now;
    }
}
