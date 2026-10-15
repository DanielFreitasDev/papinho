package io.github.labdev2025cmd.papinho.messaging;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface MessageStarRepository extends JpaRepository<MessageStar, MessageStar.Key> {

    @Query("select s from MessageStar s where s.key.userId = :userId and s.key.messageId in :messageIds")
    List<MessageStar> findByUserAndMessages(UUID userId, Collection<UUID> messageIds);

    @Query("select s from MessageStar s where s.key.userId = :userId order by s.createdAt desc")
    List<MessageStar> findByUser(UUID userId);

    /** Remove todas as linhas da mensagem (apagar para todos, MS-06). */
    @Modifying
    @Query("delete from MessageStar s where s.key.messageId = :messageId")
    void deleteByMessage(UUID messageId);
}
