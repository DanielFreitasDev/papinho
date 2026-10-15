package io.github.labdev2025cmd.papinho.presence;

import java.security.Principal;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/** Liga as conexões STOMP e os heartbeats de {@code /app/presence} ao {@link PresenceService}. */
@Controller
class PresenceListener {

    private final PresenceService presence;

    PresenceListener(PresenceService presence) {
        this.presence = presence;
    }

    @EventListener
    void on(SessionConnectedEvent event) {
        UUID user = userId(event.getUser());
        String connection = SimpMessageHeaderAccessor.getSessionId(event.getMessage().getHeaders());
        if (user != null && connection != null) {
            presence.connected(user, connection);
        }
    }

    @EventListener
    void on(SessionDisconnectEvent event) {
        UUID user = userId(event.getUser());
        if (user != null) {
            presence.disconnected(user, event.getSessionId());
        }
    }

    @MessageMapping("/presence")
    void heartbeat(HeartbeatCommand command, Principal principal, SimpMessageHeaderAccessor headers) {
        UUID user = userId(principal);
        String connection = headers.getSessionId();
        if (user != null && connection != null) {
            presence.heartbeat(user, connection, command.active());
        }
    }

    private static @Nullable UUID userId(@Nullable Principal principal) {
        return principal == null ? null : UUID.fromString(principal.getName());
    }

    /** @param active a aba está visível e em uso */
    record HeartbeatCommand(boolean active) {
    }
}
