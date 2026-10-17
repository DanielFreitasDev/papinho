package io.github.labdev2025cmd.papinho.presence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import io.github.labdev2025cmd.papinho.messaging.ConversationService;
import io.github.labdev2025cmd.papinho.privacy.PrivacyService;
import io.github.labdev2025cmd.papinho.support.MutableClock;

/** Heartbeat de presença (RT-03): sem batida por 45 s, a conexão deixa de contar como online. */
class PresenceServiceTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-17T12:00:00Z"));
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PresenceService presence = new PresenceService(mock(ConversationService.class),
            mock(PrivacyService.class), mock(SimpMessagingTemplate.class), jdbc, clock);

    @Test
    @DisplayName("a varredura tira do online a conexão que parou de bater e grava o visto por último")
    void shouldExpireStaleHeartbeat() {
        UUID ana = UUID.randomUUID();
        presence.connected(ana, "aba");
        presence.heartbeat(ana, "aba", true);
        assertThat(presence.isOnline(ana)).isTrue();

        clock.advance(PresenceService.STALE_AFTER.minusSeconds(1));
        presence.sweep();
        assertThat(presence.isOnline(ana)).as("ainda dentro do prazo").isTrue();
        verify(jdbc, never()).update(anyString(), any(Object[].class));

        clock.advance(Duration.ofSeconds(2));
        presence.sweep();
        assertThat(presence.isOnline(ana)).as("45 s sem heartbeat").isFalse();
        verify(jdbc).update(startsWith("insert into user_presence"), any(Object[].class));
    }

    @Test
    @DisplayName("aba em segundo plano não conta como online, mesmo batendo")
    void shouldIgnoreBackgroundTab() {
        UUID ana = UUID.randomUUID();
        presence.heartbeat(ana, "aba", false);
        assertThat(presence.isOnline(ana)).isFalse();
    }
}
