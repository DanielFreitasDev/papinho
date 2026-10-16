package io.github.labdev2025cmd.papinho.media;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface MediaFileRepository extends JpaRepository<MediaFile, UUID> {

    @Query("select coalesce(sum(f.size), 0) from MediaFile f where f.ownerId = :ownerId")
    long usedBy(UUID ownerId);

    @Query("select f from MediaFile f where f.ownerId = :ownerId order by f.createdAt")
    java.util.List<MediaFile> findByOwner(UUID ownerId);
}
