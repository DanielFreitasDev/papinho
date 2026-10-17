package io.github.labdev2025cmd.papinho.presence;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.modulith.events.ApplicationModuleListener;

import io.github.labdev2025cmd.papinho.messaging.ConversationService;
import io.github.labdev2025cmd.papinho.privacy.PrivacyEvents;
import io.github.labdev2025cmd.papinho.privacy.PrivacyItem;
import io.github.labdev2025cmd.papinho.privacy.PrivacyService;
import io.github.labdev2025cmd.papinho.shared.error.ApiException;

/**
 * Estado online por usuário a partir das conexões WebSocket. Cada conexão manda um heartbeat dizendo se a aba
 * está visível; a pessoa está online se alguma conexão estiver ativa e com heartbeat recente.
 */
@Service
public class PresenceService {

    /** Sem heartbeat por este tempo, a conexão deixa de contar como ativa (o cliente manda a cada 20 s). */
    static final Duration STALE_AFTER = Duration.ofSeconds(45);
    static final String DESTINATION = "/queue/events";

    private final Map<UUID, Map<String, Beat>> connections = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> online = new ConcurrentHashMap<>();
    private final ConversationService conversations;
    private final PrivacyService privacy;
    private final SimpMessagingTemplate broker;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    PresenceService(ConversationService conversations, PrivacyService privacy, SimpMessagingTemplate broker,
            JdbcTemplate jdbc, Clock clock) {
        this.conversations = conversations;
        this.privacy = privacy;
        this.broker = broker;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Conexão nova: ainda não ativa até o primeiro heartbeat de aba visível. */
    void connected(UUID userId, String connectionId) {
        connections.computeIfAbsent(userId, id -> new ConcurrentHashMap<>()).put(connectionId,
                new Beat(false, clock.instant()));
    }

    void disconnected(UUID userId, String connectionId) {
        Map<String, Beat> beats = connections.get(userId);
        if (beats != null) {
            beats.remove(connectionId);
            if (beats.isEmpty()) {
                connections.remove(userId, beats);
            }
        }
        refresh(userId);
    }

    /** Heartbeat da conexão; {@code active} falso quando a aba está em segundo plano (RT-03). */
    void heartbeat(UUID userId, String connectionId, boolean active) {
        connections.computeIfAbsent(userId, id -> new ConcurrentHashMap<>()).put(connectionId,
                new Beat(active, clock.instant()));
        refresh(userId);
    }

    /** Varredura: conexões sem heartbeat recente deixam de contar (aba congelada, rede caída). */
    @Scheduled(fixedDelay = 15_000)
    void sweep() {
        Set.copyOf(online.keySet()).forEach(this::refresh);
    }

    /**
     * Presença de {@code userId} vista por {@code viewer}; só para quem tem conversa aceita com ele. Online e visto
     * por último seguem a visibilidade de cada um, com reciprocidade (PV-04, PV-05): o que não pode ser visto volta
     * como offline e sem horário, igual a quem nunca ficou online.
     */
    public PresenceView view(UUID viewer, UUID userId) {
        if (!viewer.equals(userId) && !conversations.mutualPeers(viewer).contains(userId)) {
            throw new NotVisibleException();
        }
        return filtered(viewer, userId, isOnline(userId), lastSeen(userId).orElse(null));
    }

    /** Mudou quem vê a presença: reenvia o estado filtrado para cada par (PV-04, PV-05). */
    @ApplicationModuleListener
    void on(PrivacyEvents.PresenceVisibilityChanged event) {
        UUID userId = event.userId();
        boolean now = isOnline(userId);
        @Nullable Instant lastSeen = lastSeen(userId).orElse(null);
        for (UUID peer : conversations.mutualPeers(userId)) {
            PresenceView view = filtered(peer, userId, now, lastSeen);
            broker.convertAndSendToUser(peer.toString(), DESTINATION,
                    new PresenceEvent("presence", userId, view.online(), view.lastSeenAt()));
            // Pela reciprocidade, o que a própria pessoa vê dos pares também mudou.
            PresenceView back = filtered(userId, peer, isOnline(peer), lastSeen(peer).orElse(null));
            broker.convertAndSendToUser(userId.toString(), DESTINATION,
                    new PresenceEvent("presence", peer, back.online(), back.lastSeenAt()));
        }
    }

    private PresenceView filtered(UUID viewer, UUID userId, boolean online, @Nullable Instant lastSeen) {
        boolean seeOnline = privacy.canSeePresence(viewer, userId, PrivacyItem.ONLINE);
        boolean seeLastSeen = privacy.canSeePresence(viewer, userId, PrivacyItem.LAST_SEEN);
        return new PresenceView(userId, seeOnline && online, seeLastSeen ? lastSeen : null);
    }

    boolean isOnline(UUID userId) {
        Map<String, Beat> beats = connections.get(userId);
        if (beats == null) {
            return false;
        }
        Instant limit = clock.instant().minus(STALE_AFTER);
        return beats.values().stream().anyMatch(beat -> beat.active() && beat.at().isAfter(limit));
    }

    private void refresh(UUID userId) {
        boolean now = isOnline(userId);
        Boolean before = now ? online.put(userId, true) : online.remove(userId);
        boolean was = before != null && before;
        if (was == now) {
            return;
        }
        @Nullable Instant previous = lastSeen(userId).orElse(null);
        @Nullable Instant next = previous;
        if (!now) {
            next = clock.instant();
            // A conta pode ter sido excluída antes de a conexão cair: aí não há o que gravar (nem erro de FK).
            jdbc.update("insert into user_presence (user_id, last_seen_at) select id, ? from user_accounts where id = ? "
                    + "on conflict (user_id) do update set last_seen_at = excluded.last_seen_at",
                    next.atOffset(ZoneOffset.UTC), userId);
        }
        // Só avisa quem passa a ver algo diferente: um evento a cada transição, mesmo com o conteúdo filtrado,
        // daria a hora exata de cada entrada e saída de quem escondeu a presença (PV-04, PV-05).
        for (UUID peer : conversations.mutualPeers(userId)) {
            PresenceView seen = filtered(peer, userId, was, previous);
            PresenceView view = filtered(peer, userId, now, next);
            if (!view.equals(seen)) {
                broker.convertAndSendToUser(peer.toString(), DESTINATION,
                        new PresenceEvent("presence", userId, view.online(), view.lastSeenAt()));
            }
        }
    }

    private Optional<Instant> lastSeen(UUID userId) {
        return jdbc.query("select last_seen_at from user_presence where user_id = ?",
                (rs, index) -> rs.getObject("last_seen_at", OffsetDateTime.class).toInstant(), userId).stream()
                .findFirst();
    }

    private record Beat(boolean active, Instant at) {
    }

    /** @param lastSeenAt última vez online; nulo se nunca ficou offline desde o cadastro */
    public record PresenceView(UUID userId, boolean online, @Nullable Instant lastSeenAt) {
    }

    record PresenceEvent(String type, UUID userId, boolean online, @Nullable Instant lastSeenAt) {
    }

    static class NotVisibleException extends ApiException {
        NotVisibleException() {
            super(HttpStatus.NOT_FOUND, "not-found", "Presence not visible");
        }
    }
}
