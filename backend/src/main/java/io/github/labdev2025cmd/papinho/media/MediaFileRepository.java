package io.github.labdev2025cmd.papinho.media;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface MediaFileRepository extends JpaRepository<MediaFile, UUID> {

    @Query("select coalesce(sum(f.size), 0) from MediaFile f where f.ownerId = :ownerId")
    long usedBy(UUID ownerId);

    @Query("select f from MediaFile f where f.ownerId = :ownerId order by f.createdAt")
    List<MediaFile> findByOwner(UUID ownerId);

    /**
     * Trava os arquivos mais antigos que {@code cutoff} ainda não conferidos (MD-01). A trava faz um anexo criado
     * ao mesmo tempo esperar (a FK da mensagem pede FOR KEY SHARE); os que já estão travados por um anexo em
     * andamento ficam para a próxima rodada.
     */
    @Query(value = """
            select id from media_files where not in_use and created_at < :cutoff
            order by created_at limit :limit for update skip locked
            """, nativeQuery = true)
    List<UUID> lockUnchecked(Instant cutoff, int limit);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update MediaFile f set f.inUse = true where f.id in :ids")
    void markInUse(Collection<UUID> ids);
}
