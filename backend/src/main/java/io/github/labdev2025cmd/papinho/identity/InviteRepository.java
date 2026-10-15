package io.github.labdev2025cmd.papinho.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

interface InviteRepository extends JpaRepository<Invite, UUID> {

    Optional<Invite> findByTokenHash(String tokenHash);

    /** Trava o convite para consumir um uso sem corrida entre dois cadastros simultâneos. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invite i where i.tokenHash = :tokenHash")
    Optional<Invite> lockByTokenHash(String tokenHash);

    List<Invite> findTop50ByCreatedByOrderByCreatedAtDesc(UUID createdBy);

    Optional<Invite> findByIdAndCreatedBy(UUID id, UUID createdBy);
}
