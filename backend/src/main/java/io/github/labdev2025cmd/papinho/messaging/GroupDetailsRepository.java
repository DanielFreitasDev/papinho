package io.github.labdev2025cmd.papinho.messaging;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface GroupDetailsRepository extends JpaRepository<GroupDetails, UUID> {

    Optional<GroupDetails> findByInviteCode(String inviteCode);

    boolean existsByPhotoMediaId(UUID photoMediaId);

    /** A foto é de um grupo de que {@code viewer} é membro ativo (acesso à mídia)? */
    @Query(value = """
            select exists (select 1 from group_details g
              join conversation_members cm on cm.conversation_id = g.conversation_id
             where g.photo_media_id = :mediaId and cm.user_id = :viewer and cm.state = 'ACTIVE')
            """, nativeQuery = true)
    boolean photoVisibleTo(UUID viewer, UUID mediaId);
}
