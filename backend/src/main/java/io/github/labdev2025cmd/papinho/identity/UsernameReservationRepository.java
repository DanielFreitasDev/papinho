package io.github.labdev2025cmd.papinho.identity;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface UsernameReservationRepository extends JpaRepository<UsernameReservation, String> {

    @Modifying
    @Query("delete from UsernameReservation r where r.reservedUntil < :now")
    int deleteExpired(Instant now);
}
