package io.github.labdev2025cmd.papinho.notification;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSource;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import io.github.labdev2025cmd.papinho.messaging.MessagePosted;
import io.github.labdev2025cmd.papinho.messaging.NotificationRecipients;
import io.github.labdev2025cmd.papinho.privacy.PrivacyService;
import tools.jackson.databind.json.JsonMapper;

/**
 * Decide, no servidor, quem é avisado de uma mensagem nova e com que texto (NT-01, NT-03, PV-08, PV-03):
 * <ul>
 * <li>nada para quem desligou as notificações, o tipo de conversa (individual ou grupo) ou para mensagem
 * silenciosa (MS-11);</li>
 * <li>conversa silenciada (GR-05) só avisa menção, e só se a pessoa pediu;</li>
 * <li>"ocultar conteúdo" (PV-08), conversa trancada (PV-03) e visualização única mostram só "Nova mensagem".</li>
 * </ul>
 * O aviso vai pelo WebSocket (app aberto) e por Web Push (app fechado) com a mesma {@code tag}: no navegador as
 * duas notificações se substituem em vez de duplicar.
 */
@Component
class NotificationDispatcher {

    private static final Locale LOCALE = Locale.forLanguageTag("pt-BR");

    private final NotificationRecipients recipients;
    private final NotificationPreferences preferences;
    private final PrivacyService privacy;
    private final PushSubscriptions subscriptions;
    private final PushGateway gateway;
    private final PushProperties properties;
    private final SimpMessagingTemplate broker;
    private final MessageSource texts;
    private final JsonMapper json;
    private final PushSessions pushSessions;

    NotificationDispatcher(NotificationRecipients recipients, NotificationPreferences preferences,
            PrivacyService privacy, PushSubscriptions subscriptions, PushGateway gateway, PushProperties properties,
            SimpMessagingTemplate broker, MessageSource texts, JsonMapper json, PushSessions pushSessions) {
        this.recipients = recipients;
        this.preferences = preferences;
        this.privacy = privacy;
        this.subscriptions = subscriptions;
        this.gateway = gateway;
        this.properties = properties;
        this.broker = broker;
        this.texts = texts;
        this.json = json;
        this.pushSessions = pushSessions;
    }

    @ApplicationModuleListener
    void on(MessagePosted event) {
        for (NotificationRecipients.Recipient recipient : recipients.of(event.messageId())) {
            Notification notification = decide(recipient, preferences.of(recipient.userId()),
                    privacy.hidesNotificationContent(recipient.userId()));
            if (notification != null) {
                deliver(recipient.userId(), notification.withBadge(recipients.unreadTotal(recipient.userId())));
            }
        }
    }

    /** A regra em si, sem efeitos: nulo quando não avisa. */
    @Nullable Notification decide(NotificationRecipients.Recipient recipient, NotificationPreferences.Preferences prefs,
            boolean hideContent) {
        if (!prefs.enabled() || recipient.silent()) {
            return null;
        }
        boolean mention = recipient.group() && recipient.mentioned();
        if (recipient.muted() && !(recipient.muteMentions() && recipient.mentioned())) {
            return null;
        }
        if (recipient.group() ? !prefs.groups() && !(mention && prefs.mentions()) : !prefs.direct()) {
            return null;
        }
        String tag = "conversation-" + recipient.conversationId();
        if (hideContent || recipient.hidden()) {
            return new Notification(recipient.conversationId(), text("notification.app"),
                    text("notification.hidden"), tag, !prefs.sound(), null);
        }
        String groupName = recipient.groupName();
        String title = recipient.group() && groupName != null ? groupName : recipient.senderLabel();
        String body = recipient.group() ? recipient.senderLabel() + ": " + recipient.preview() : recipient.preview();
        return new Notification(recipient.conversationId(), title, body, tag, !prefs.sound(), null);
    }

    private void deliver(UUID userId, Notification notification) {
        broker.convertAndSendToUser(userId.toString(), "/queue/events", new NotifyEvent("notify",
                notification.conversationId(), notification.title(), notification.body(), notification.tag(),
                notification.silent(), notification.badge()));
        if (!properties.enabled()) {
            return;
        }
        byte[] payload = json.writeValueAsString(notification).getBytes(StandardCharsets.UTF_8);
        List<PushSubscriptions.Subscription> targets = subscriptions.of(userId);
        Set<String> live = targets.isEmpty() ? Set.of() : pushSessions.liveKeys(userId);
        for (PushSubscriptions.Subscription subscription : targets) {
            if (subscription.sessionKey() == null || !live.contains(subscription.sessionKey())) {
                // A sessão que assinou acabou (revogada, senha trocada, conta desativada, expirada): sem push.
                subscriptions.forget(subscription.id());
                continue;
            }
            switch (gateway.send(subscription, payload)) {
                case GONE -> subscriptions.forget(subscription.id());
                case DELIVERED -> subscriptions.markUsed(subscription.id());
                case FAILED -> {
                    // Fica para a próxima mensagem; o push é melhor esforço.
                }
            }
        }
    }

    private String text(String key) {
        return texts.getMessage(key, null, key, LOCALE);
    }

    /**
     * O que o navegador mostra; o mesmo formato vai no push (lido pelo service worker).
     *
     * @param silent sem som (a pessoa desligou o som)
     * @param badge total de não lidas para o contador no ícone
     */
    record Notification(UUID conversationId, String title, String body, String tag, boolean silent,
            @Nullable Long badge) {

        Notification withBadge(long total) {
            return new Notification(conversationId, title, body, tag, silent, total);
        }
    }

    record NotifyEvent(String type, UUID conversationId, String title, String body, String tag, boolean silent,
            @Nullable Long badge) {
    }
}
