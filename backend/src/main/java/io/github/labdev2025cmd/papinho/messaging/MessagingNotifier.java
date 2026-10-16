package io.github.labdev2025cmd.papinho.messaging;

import java.util.Set;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

    @EventListener
    void on(MessagingEvents.MessagesChanged event) {
        afterCommit(() -> send(event.recipients(),
                new MessagesEvent("messages", event.conversationId(), event.lastSeq())));
    }

    @EventListener
    void on(MessagingEvents.ReceiptsChanged event) {
        afterCommit(() -> send(event.recipients(), new ReceiptsEvent("receipts", event.conversationId(),
                event.userId(), event.deliveredSeq(), event.readSeq())));
    }

    @EventListener
    void on(MessagingEvents.ConversationUpdated event) {
        afterCommit(() -> send(event.recipients(),
                new ConversationEvent("conversation", event.conversationId())));
    }

    @EventListener
    void on(MessagingEvents.MessagesHidden event) {
        afterCommit(() -> send(Set.of(event.userId()),
                new HiddenEvent("hidden", event.conversationId(), event.messageIds())));
    }

    @EventListener
    void on(MessagingEvents.ConversationCleared event) {
        afterCommit(() -> send(Set.of(event.userId()),
                new ConversationEvent("cleared", event.conversationId())));
    }

    @EventListener
    void on(MessagingEvents.FoldersChanged event) {
        afterCommit(() -> send(Set.of(event.userId()), new FoldersEvent("folders")));
    }

    /**
     * Manda depois do commit, sem passar pelo registro de eventos do Modulith: o aviso é efêmero (o cliente
     * sincroniza pela API) e um @TransactionalEventListener faria cada envio pedir uma segunda conexão ao banco
     * para registrar a publicação, o que esgotava o pool com muitos envios ao mesmo tempo.
     */
    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
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

    record FoldersEvent(String type) {
    }

    /** "digitando…" ("typing") ou "gravando áudio…" ("recording"); {@code activity} vazio quando parou (MS-03). */
    record ActivityEvent(String type, UUID conversationId, UUID userId, String activity) {
    }
}
