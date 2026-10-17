package io.github.labdev2025cmd.papinho.identity;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

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

    /** Nulo depois que a conta dona é excluída: o nome segue reservado para ninguém até vencer. */
    @Column
    private @Nullable UUID userId;

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

    boolean blocks(UUID candidate, Instant now) {
        return reservedUntil.isAfter(now) && !candidate.equals(userId);
    }

    void renew(UUID owner, Instant until) {
        this.userId = owner;
        this.reservedUntil = until;
    }
}
