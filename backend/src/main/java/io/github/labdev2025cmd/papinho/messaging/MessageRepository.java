package io.github.labdev2025cmd.papinho.messaging;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface MessageRepository extends JpaRepository<Message, UUID> {

    Optional<Message> findBySenderIdAndClientMessageId(UUID senderId, UUID clientMessageId);

    /** Alguma mensagem ainda usa o arquivo (encaminhar copia a referência)? */
    boolean existsByAttachmentId(UUID attachmentId);

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

    /** Galeria (MD-03): mensagens com anexo destes tipos, da mais nova para a mais antiga. */
    @Query("select m from Message m where m.conversationId = :conversationId and m.attachmentKind in :kinds "
            + "and m.seq < :beforeSeq order by m.seq desc")
    List<Message> findWithAttachments(UUID conversationId, java.util.Collection<String> kinds, long beforeSeq,
            Limit limit);

    /** Galeria de links (MD-03): mensagens cujo texto tem um endereço http(s). */
    @Query(value = """
            select * from messages m where m.conversation_id = :conversationId and m.seq < :beforeSeq
               and m.body ~* 'https?://[^[:space:]]+' order by m.seq desc
            """, nativeQuery = true)
    List<Message> findWithLinks(UUID conversationId, long beforeSeq, Limit limit);
}
