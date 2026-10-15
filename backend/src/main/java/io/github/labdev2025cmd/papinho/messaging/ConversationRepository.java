package io.github.labdev2025cmd.papinho.messaging;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByDirectKey(String directKey);
}
