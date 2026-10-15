package io.github.labdev2025cmd.papinho.identity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * {@code @usuario} antigo, reservado ao dono anterior por 30 dias depois de uma troca (ID-03), para que outra
 * pessoa não o use para se passar por ele. Só o próprio dono pode retomá-lo nesse período.
 */
@Entity
@Table(name = "username_reservations")
class UsernameReservation {

    @Id
    @Column(length = Username.MAX_LENGTH)
    private String username;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private Instant reservedUntil;

    protected UsernameReservation() {
        this.username = "";
        this.userId = new UUID(0, 0);
        this.reservedUntil = Instant.EPOCH;
    }

    UsernameReservation(Username username, UUID userId, Instant reservedUntil) {
        this.username = username.value();
        this.userId = userId;
        this.reservedUntil = reservedUntil;
    }

    UUID userId() {
        return userId;
    }

    boolean blocks(UUID candidate, Instant now) {
        return reservedUntil.isAfter(now) && !userId.equals(candidate);
    }

    void renew(UUID owner, Instant until) {
        this.userId = owner;
        this.reservedUntil = until;
    }
}
