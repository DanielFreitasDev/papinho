package io.github.labdev2025cmd.papinho.messaging;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.labdev2025cmd.papinho.media.MediaAccessPolicy;

/** Quem participa de uma conversa abre os anexos das mensagens dela e a foto do grupo (MD-01, GR-01, SG-01). */
@Component
class MessageMediaAccess implements MediaAccessPolicy {

    private final MessageRepository messages;
    private final GroupDetailsRepository groups;

    MessageMediaAccess(MessageRepository messages, GroupDetailsRepository groups) {
        this.messages = messages;
        this.groups = groups;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canRead(UUID viewer, UUID mediaId) {
        return messages.attachmentVisibleTo(viewer, mediaId) || groups.photoVisibleTo(viewer, mediaId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean restricted(UUID mediaId) {
        return messages.viewOnceAttachment(mediaId);
    }
}
