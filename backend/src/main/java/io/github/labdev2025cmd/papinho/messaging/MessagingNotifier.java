package io.github.labdev2025cmd.papinho.messaging;

import java.util.Set;
import java.util.UUID;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Leva os eventos do módulo às filas dos usuários ({@code /user/queue/events}) depois do commit. O aviso é
 * pequeno de propósito: o cliente busca o conteúdo pela API (ADR 0004 e 0010).
 */
@Component
class MessagingNotifier {

    static final String DESTINATION = "/queue/events";

    private final SimpMessagingTemplate broker;

    MessagingNotifier(SimpMessagingTemplate broker) {
        this.broker = broker;
    }

    @TransactionalEventListener
    void on(MessagingEvents.MessagesChanged event) {
        send(event.recipients(), new MessagesEvent("messages", event.conversationId(), event.lastSeq()));
    }

    @TransactionalEventListener
    void on(MessagingEvents.ReceiptsChanged event) {
        send(event.recipients(), new ReceiptsEvent("receipts", event.conversationId(), event.userId(),
                event.deliveredSeq(), event.readSeq()));
    }

    @TransactionalEventListener
    void on(MessagingEvents.ConversationUpdated event) {
        send(event.recipients(), new ConversationEvent("conversation", event.conversationId()));
    }

    @TransactionalEventListener
    void on(MessagingEvents.MessagesHidden event) {
        send(Set.of(event.userId()), new HiddenEvent("hidden", event.conversationId(), event.messageIds()));
    }

    void send(Set<UUID> recipients, Object payload) {
        // O nome do principal no WebSocket é o id da conta (PapinhoUser.getUsername()).
        recipients.forEach(user -> broker.convertAndSendToUser(user.toString(), DESTINATION, payload));
    }

    record MessagesEvent(String type, UUID conversationId, long lastSeq) {
    }

    record ReceiptsEvent(String type, UUID conversationId, UUID userId, long deliveredSeq, long readSeq) {
    }

    record HiddenEvent(String type, UUID conversationId, java.util.List<UUID> messageIds) {
    }

    record ConversationEvent(String type, UUID conversationId) {
    }

    /** "digitando…" ("typing") ou "gravando áudio…" ("recording"); {@code activity} vazio quando parou (MS-03). */
    record ActivityEvent(String type, UUID conversationId, UUID userId, String activity) {
    }
}
