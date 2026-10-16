package io.github.labdev2025cmd.papinho.notification;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.labdev2025cmd.papinho.shared.security.CurrentUser;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Preferências de notificação (NT-03) e assinaturas de Web Push (NT-01). */
@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController {

    private final NotificationPreferences preferences;
    private final PushSubscriptions subscriptions;
    private final VapidKeys vapid;
    private final PushProperties properties;
    private final PushSessions pushSessions;

    NotificationController(NotificationPreferences preferences, PushSubscriptions subscriptions, VapidKeys vapid,
            PushProperties properties, PushSessions pushSessions) {
        this.preferences = preferences;
        this.subscriptions = subscriptions;
        this.vapid = vapid;
        this.properties = properties;
        this.pushSessions = pushSessions;
    }

    @GetMapping("/preferences")
    NotificationPreferences.Preferences preferences() {
        return preferences.of(CurrentUser.id());
    }

    @PutMapping("/preferences")
    NotificationPreferences.Preferences change(@RequestBody NotificationPreferences.Preferences body) {
        return preferences.change(CurrentUser.id(), body);
    }

    /** Chave pública VAPID ({@code applicationServerKey}); {@code enabled} falso quando o push está desligado. */
    @GetMapping("/push")
    PushInfo push() {
        return new PushInfo(properties.enabled(), properties.enabled() ? vapid.current().publicKey() : null);
    }

    @PostMapping("/push/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void subscribe(@Valid @RequestBody SubscribeRequest body, HttpSession session) {
        subscriptions.subscribe(CurrentUser.id(), body.endpoint(), body.keys().p256dh(), body.keys().auth(),
                pushSessions.keyOf(session));
    }

    @DeleteMapping("/push/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unsubscribe(@Valid @RequestBody UnsubscribeRequest body) {
        subscriptions.unsubscribe(CurrentUser.id(), body.endpoint());
    }

    record PushInfo(boolean enabled, @org.jspecify.annotations.Nullable String publicKey) {
    }

    /** O formato de {@code PushSubscription.toJSON()} do navegador. */
    record SubscribeRequest(@NotBlank @Size(max = 1024) String endpoint, @NotNull @Valid Keys keys) {
    }

    record Keys(@NotBlank @Size(max = 100) String p256dh, @NotBlank @Size(max = 40) String auth) {
    }

    record UnsubscribeRequest(@NotBlank @Size(max = 1024) String endpoint) {
    }
}
