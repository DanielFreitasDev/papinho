package io.github.labdev2025cmd.papinho.contact;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface BlockRepository extends JpaRepository<Block, Block.Key> {

    @Query("select b from Block b where b.key.blockerId = :blockerId order by b.createdAt desc")
    List<Block> findByBlocker(UUID blockerId);

    /** Bloqueios entre {@code userId} e alguém de {@code others}, em qualquer direção. */
    @Query("select b from Block b where (b.key.blockerId = :userId and b.key.blockedId in :others)"
            + " or (b.key.blockedId = :userId and b.key.blockerId in :others)")
    List<Block> findBetween(UUID userId, Collection<UUID> others);
}
