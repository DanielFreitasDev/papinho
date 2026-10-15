package io.github.labdev2025cmd.papinho.messaging;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface MessageRepository extends JpaRepository<Message, UUID> {

    Optional<Message> findBySenderIdAndClientMessageId(UUID senderId, UUID clientMessageId);

    /** Alterações depois de {@code afterSeq}, na ordem em que aconteceram (sincronização, RT-01). */
    @Query("select m from Message m where m.conversationId = :conversationId and m.changeSeq > :afterSeq "
            + "order by m.changeSeq")
    List<Message> findChangedAfter(UUID conversationId, long afterSeq, Limit limit);

    /** Página de histórico antes de {@code beforeSeq}, da mais nova para a mais antiga. */
    @Query("select m from Message m where m.conversationId = :conversationId and m.seq < :beforeSeq "
            + "order by m.seq desc")
    List<Message> findBefore(UUID conversationId, long beforeSeq, Limit limit);

    @Query("select m from Message m where m.conversationId = :conversationId and m.pinnedAt is not null "
            + "order by m.pinnedAt")
    List<Message> findPinned(UUID conversationId);

    /** O arquivo está em alguma mensagem de uma conversa visível para {@code viewer} (acesso à mídia)? */
    @Query(value = """
            select exists (select 1 from messages m
              join conversation_members cm on cm.conversation_id = m.conversation_id
             where m.attachment_id = :mediaId and cm.user_id = :viewer and cm.state in ('ACTIVE', 'REQUEST'))
            """, nativeQuery = true)
    boolean attachmentVisibleTo(UUID viewer, UUID mediaId);
}
