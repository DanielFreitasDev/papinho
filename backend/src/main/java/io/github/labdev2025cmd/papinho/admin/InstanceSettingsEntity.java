package io.github.labdev2025cmd.papinho.admin;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Linha única ({@code id = 1}) com as configurações da instância. */
@Entity
@Table(name = "instance_settings")
class InstanceSettingsEntity {

    static final short SINGLETON_ID = 1;

    @Id
    private short id = SINGLETON_ID;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RegistrationMode registrationMode = RegistrationMode.INVITE_ONLY;

    @Column(nullable = false)
    private int usernameChangeCooldownDays = 30;

    @Column(nullable = false)
    private int messageEditWindowMinutes = 15;

    @Column(nullable = false)
    private int messageDeleteWindowHours = 48;

    @Column(nullable = false)
    private long userQuotaBytes = 5L * 1024 * 1024 * 1024;

    @Column(nullable = false)
    private boolean breachedPasswordCheck;

    @Column(nullable = false)
    private Instant updatedAt = Instant.EPOCH;

    @Version
    private long version;

    InstanceSettings toView() {
        return new InstanceSettings(registrationMode, java.time.Duration.ofDays(usernameChangeCooldownDays),
                java.time.Duration.ofMinutes(messageEditWindowMinutes),
                java.time.Duration.ofHours(messageDeleteWindowHours), userQuotaBytes, breachedPasswordCheck);
    }

    void update(UpdateInstanceSettingsRequest request, Instant now) {
        this.registrationMode = request.registrationMode();
        this.usernameChangeCooldownDays = request.usernameChangeCooldownDays();
        this.messageEditWindowMinutes = request.messageEditWindowMinutes();
        this.messageDeleteWindowHours = request.messageDeleteWindowHours();
        this.userQuotaBytes = request.userQuotaMegabytes() * 1024 * 1024;
        this.breachedPasswordCheck = request.breachedPasswordCheck();
        this.updatedAt = now;
    }
}
