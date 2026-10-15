package io.github.labdev2025cmd.papinho.contact;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface BlockRepository extends JpaRepository<Block, Block.Key> {

    @Query("select b from Block b where b.key.blockerId = :blockerId order by b.createdAt desc")
    List<Block> findByBlocker(UUID blockerId);
}
