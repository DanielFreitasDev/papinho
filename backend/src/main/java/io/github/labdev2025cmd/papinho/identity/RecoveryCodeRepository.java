package io.github.labdev2025cmd.papinho.identity;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, UUID> {

    @Query("select c from RecoveryCode c where c.userId = :userId and c.usedAt is null")
    List<RecoveryCode> findUnused(UUID userId);

    @Modifying
    @Query("delete from RecoveryCode c where c.userId = :userId")
    void deleteAllByUserId(UUID userId);
}
