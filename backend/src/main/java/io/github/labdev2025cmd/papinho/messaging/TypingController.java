package io.github.labdev2025cmd.papinho.messaging;

import java.security.Principal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import io.github.labdev2025cmd.papinho.messaging.MessagingExceptions.NotFoundException;

/**
 * "digitando…" e "gravando áudio…" (MS-03): o cliente manda {@code /app/activity} e o servidor repassa aos
 * outros participantes ativos. Efêmero: nada vai para o banco.
 */
@Controller
class TypingController {

    /** Intervalo mínimo entre avisos iguais do mesmo usuário na mesma conversa. */
    private static final Duration MIN_INTERVAL = Duration.ofMillis(800);

    private final ConversationService conversations;
    private final MessagingNotifier notifier;
    private final Clock clock;
    private final Map<String, Instant> lastSent = new ConcurrentHashMap<>();

    TypingController(ConversationService conversations, MessagingNotifier notifier, Clock clock) {
        this.conversations = conversations;
        this.notifier = notifier;
        this.clock = clock;
    }

    @MessageMapping("/activity")
    void activity(ActivityCommand command, Principal principal) {
        UUID me = UUID.fromString(principal.getName());
        String activity = command.activity() == null ? "" : command.activity();
        if (!activity.isEmpty() && !"typing".equals(activity) && !"recording".equals(activity)) {
            return;
        }
        String key = me + ":" + command.conversationId() + ":" + activity;
        Instant now = clock.instant();
        Instant previous = lastSent.get(key);
        if (previous != null && Duration.between(previous, now).compareTo(MIN_INTERVAL) < 0) {
            return;
        }
        lastSent.put(key, now);
        Set<UUID> audience;
        try {
            audience = conversations.typingAudience(me, command.conversationId());
        } catch (NotFoundException hidden) {
            return;
        }
        notifier.send(audience, new MessagingNotifier.ActivityEvent("activity", command.conversationId(), me,
                activity));
        if (lastSent.size() > 10_000) {
            lastSent.clear();
        }
    }

    /** {@code activity}: "typing", "recording" ou vazio/nulo para parar. */
    record ActivityCommand(UUID conversationId, @Nullable String activity) {
    }
}
