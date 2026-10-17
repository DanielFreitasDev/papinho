package io.github.labdev2025cmd.papinho.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import io.github.labdev2025cmd.papinho.shared.security.RateLimiter;

/** Validade do token de desbloqueio (PV-03): 10 minutos parado, 1 hora no máximo, com o relógio controlado. */
class ConversationLocksTest {

    private final UUID user = UUID.randomUUID();
    private final UUID code = UUID.randomUUID();
    private final UUID conversation = UUID.randomUUID();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-17T12:00:00Z"));
    private ConversationLocks locks;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.<Object>of((Object) new String[] {code.toString(), "hash"}));
        when(jdbc.queryForList(anyString(), any(SqlParameterSource.class), eq(UUID.class))).thenReturn(List.of(code));
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.matches(any(), any())).thenReturn(true);
        locks = new ConversationLocks(jdbc, encoder, mock(ConversationMembership.class), mock(RateLimiter.class),
                clock);
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("parado por 10 minutos, o token deixa de abrir a conversa")
    void shouldExpireAfterIdle() {
        useToken(locks.unlock(user, "4321").token());
        clock.advance(Duration.ofMinutes(9));
        assertThat(locks.hidden(user, conversation)).as("9 min depois, e o uso renova").isFalse();
        clock.advance(Duration.ofMinutes(9));
        assertThat(locks.hidden(user, conversation)).as("9 min depois do último uso").isFalse();
        clock.advance(ConversationLocks.IDLE);
        assertThat(locks.hidden(user, conversation)).as("10 min parado").isTrue();
    }

    @Test
    @DisplayName("mesmo em uso, o token vence 1 hora depois de criado")
    void shouldExpireAfterMaxLifetime() {
        useToken(locks.unlock(user, "4321").token());
        for (int i = 0; i < 6; i++) {
            clock.advance(Duration.ofMinutes(9));
            assertThat(locks.hidden(user, conversation)).isFalse();
        }
        clock.advance(Duration.ofMinutes(6));
        assertThat(locks.hidden(user, conversation)).as("1 hora depois, mesmo em uso").isTrue();
    }

    private static void useToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ConversationLocks.HEADER, token);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
