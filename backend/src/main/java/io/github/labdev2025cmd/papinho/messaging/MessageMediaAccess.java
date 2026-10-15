package io.github.labdev2025cmd.papinho.messaging;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.labdev2025cmd.papinho.media.MediaAccessPolicy;

/** Quem participa de uma conversa abre os anexos das mensagens dela (MD-01, SG-01). */
@Component
class MessageMediaAccess implements MediaAccessPolicy {

    private final MessageRepository messages;

    MessageMediaAccess(MessageRepository messages) {
        this.messages = messages;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canRead(UUID viewer, UUID mediaId) {
        return messages.attachmentVisibleTo(viewer, mediaId);
    }
}
