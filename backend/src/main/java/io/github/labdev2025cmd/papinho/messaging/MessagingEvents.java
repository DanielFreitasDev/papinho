package io.github.labdev2025cmd.papinho.messaging;

import java.util.Set;
import java.util.UUID;

/** Eventos internos publicados nas transações do módulo; viram avisos pelo WebSocket depois do commit. */
final class MessagingEvents {

    private MessagingEvents() {
    }

    /** Mensagens novas ou alteradas: o cliente sincroniza por {@code afterSeq}. */
    record MessagesChanged(UUID conversationId, long lastSeq, Set<UUID> recipients) {
    }

    /** Ponteiros de entrega e leitura de {@code userId} avançaram; {@code readSeq} já filtrado pela privacidade. */
    record ReceiptsChanged(UUID conversationId, UUID userId, long deliveredSeq, long readSeq, Set<UUID> recipients) {
    }

    /** A conversa mudou para quem está em {@code recipients} (nova, aceita, recusada, marcada como não lida). */
    record ConversationUpdated(UUID conversationId, Set<UUID> recipients) {
    }
}
