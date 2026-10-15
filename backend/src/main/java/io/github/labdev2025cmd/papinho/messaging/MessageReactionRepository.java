package io.github.labdev2025cmd.papinho.messaging;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface MessageReactionRepository extends JpaRepository<MessageReaction, MessageReaction.Key> {

    @Query("select r from MessageReaction r where r.key.messageId in :messageIds order by r.createdAt")
    List<MessageReaction> findByMessages(Collection<UUID> messageIds);

    /** Remove todas as linhas da mensagem (apagar para todos, MS-06). */
    @Modifying
    @Query("delete from MessageReaction r where r.key.messageId = :messageId")
    void deleteByMessage(UUID messageId);
}
