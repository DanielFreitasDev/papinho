package io.github.labdev2025cmd.papinho.messaging;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface ConversationMemberRepository extends JpaRepository<ConversationMember, ConversationMember.Key> {

    @Query("select m from ConversationMember m where m.key.conversationId = :conversationId")
    List<ConversationMember> findByConversation(UUID conversationId);

    @Query("select m from ConversationMember m where m.key.userId = :userId "
            + "and m.state = io.github.labdev2025cmd.papinho.messaging.MemberState.ACTIVE")
    List<ConversationMember> findActiveByUser(UUID userId);

    /**
     * Quem volta a um grupo volta sem fixar (GR-05): a fixação de antes de sair não conta no limite enquanto está
     * fora, e voltando com ela daria mais de 3 fixadas.
     */
    @Modifying
    @Query(value = "update conversation_members set pinned_at = null where conversation_id = :conversationId "
            + "and user_id in (:userIds)", nativeQuery = true)
    void clearPins(UUID conversationId, Collection<UUID> userIds);
}
