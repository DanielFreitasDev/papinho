package io.github.labdev2025cmd.papinho.media;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface MediaUploadRepository extends JpaRepository<MediaUpload, UUID> {

    @Query("select coalesce(sum(u.length), 0) from MediaUpload u where u.ownerId = :ownerId")
    long reservedBy(UUID ownerId);

    List<MediaUpload> findByExpiresAtBefore(Instant now);
}
