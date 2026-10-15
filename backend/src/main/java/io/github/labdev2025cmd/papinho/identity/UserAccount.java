package io.github.labdev2025cmd.papinho.identity;

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
import jakarta.persistence.Version;

/**
 * Conta de usuário (agregado do módulo de identidade). O {@code @usuario} é guardado já normalizado; nome de
 * exibição, foto e recado ficam no módulo de perfil.
 */
@Entity
@Table(name = "user_accounts")
class UserAccount {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private @Nullable UUID id;

    @Column(nullable = false, unique = true, length = Username.MAX_LENGTH)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private boolean enabled = true;

    private @Nullable String totpSecret;

    @Column(nullable = false)
    private boolean totpEnabled;

    @Column(nullable = false)
    private Instant createdAt;

    private @Nullable Instant usernameChangedAt;

    @Version
    private long version;

    protected UserAccount() {
        this.username = "";
        this.passwordHash = "";
        this.role = Role.USER;
        this.createdAt = Instant.EPOCH;
    }

    UserAccount(Username username, String passwordHash, Role role, Instant now) {
        this.username = username.value();
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = now;
    }

    UUID id() {
        if (id == null) {
            throw new IllegalStateException("User not persisted yet");
        }
        return id;
    }

    Username username() {
        return new Username(username);
    }

    String passwordHash() {
        return passwordHash;
    }

    Role role() {
        return role;
    }

    boolean enabled() {
        return enabled;
    }

    @Nullable String totpSecret() {
        return totpSecret;
    }

    boolean totpEnabled() {
        return totpEnabled;
    }

    Instant createdAt() {
        return createdAt;
    }

    @Nullable Instant usernameChangedAt() {
        return usernameChangedAt;
    }

    void changeUsername(Username newUsername, Instant now) {
        this.username = newUsername.value();
        this.usernameChangedAt = now;
    }

    void changePassword(String newHash) {
        this.passwordHash = newHash;
    }

    void startTotpEnrollment(String encryptedSecret) {
        this.totpSecret = encryptedSecret;
        this.totpEnabled = false;
    }

    void confirmTotp() {
        if (totpSecret == null) {
            throw new IllegalStateException("TOTP enrollment not started");
        }
        this.totpEnabled = true;
    }

    void disableTotp() {
        this.totpSecret = null;
        this.totpEnabled = false;
    }

    void disable() {
        this.enabled = false;
    }

    void enable() {
        this.enabled = true;
    }
}
