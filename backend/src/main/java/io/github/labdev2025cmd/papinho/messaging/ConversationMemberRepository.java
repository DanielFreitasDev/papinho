package io.github.labdev2025cmd.papinho.messaging;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ConversationMemberRepository extends JpaRepository<ConversationMember, ConversationMember.Key> {

    @Query("select m from ConversationMember m where m.key.conversationId = :conversationId")
    List<ConversationMember> findByConversation(UUID conversationId);
}
