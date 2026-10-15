package io.github.labdev2025cmd.papinho.shared.security;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

/**
 * Limites de tentativas em memória com Bucket4j (SG-03), por chave (IP, usuário, conversa...).
 *
 * <p>Uma instância só (ADR 0004); com várias instâncias, trocar por Bucket4j sobre o PostgreSQL.
 * Baldes ociosos por mais de uma hora são descartados periodicamente.</p>
 */
@Component
public class RateLimiter {

    /** Política de um tipo de operação: capacidade e janela de recarga completa. */
    public record Policy(String name, int capacity, Duration refillPeriod) {

        public static final Policy LOGIN_BY_IP = new Policy("login-ip", 20, Duration.ofMinutes(15));
        public static final Policy LOGIN_BY_ACCOUNT = new Policy("login-account", 10, Duration.ofMinutes(15));
        public static final Policy REGISTER_BY_IP = new Policy("register-ip", 10, Duration.ofHours(1));
        public static final Policy RECOVERY_BY_IP = new Policy("recovery-ip", 10, Duration.ofHours(1));
        public static final Policy INVITE_CHECK_BY_IP = new Policy("invite-check-ip", 60, Duration.ofMinutes(10));
        public static final Policy INVITE_CREATE_BY_USER = new Policy("invite-create", 30, Duration.ofHours(1));
        public static final Policy SENSITIVE_BY_USER = new Policy("sensitive-user", 10, Duration.ofMinutes(15));
        public static final Policy REPORT_BY_USER = new Policy("report-user", 20, Duration.ofHours(1));
        /** Consultar ou usar links de convite de grupo (GR-02): impede adivinhar códigos. */
        public static final Policy GIF_BY_USER = new Policy("gif-user", 120, Duration.ofHours(1));
        public static final Policy GROUP_INVITE_BY_USER = new Policy("group-invite-user", 60, Duration.ofHours(1));
        public static final Policy UPLOAD_BY_USER = new Policy("upload-user", 200, Duration.ofHours(1));
        public static final Policy LINK_PREVIEW_BY_USER = new Policy("link-preview-user", 120, Duration.ofHours(1));
        public static final Policy TRANSCRIBE_BY_USER = new Policy("transcribe-user", 60, Duration.ofHours(1));
    }

    private final Map<String, Entry> buckets = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int multiplier;

    public RateLimiter(Clock clock, SecurityProperties properties) {
        this.clock = clock;
        this.multiplier = properties.rateLimitMultiplier();
    }

    /**
     * Consome uma ficha do balde da política para a chave.
     *
     * @throws RateLimitedException se o balde estiver vazio
     */
    public void consume(Policy policy, String key) {
        long capacity = (long) policy.capacity() * multiplier;
        Entry entry = buckets.computeIfAbsent(policy.name() + ":" + key, ignored -> new Entry(Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(capacity).refillGreedy(capacity, policy.refillPeriod()).build())
                .build()));
        entry.lastAccessMillis = clock.millis();
        ConsumptionProbe probe = entry.bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long seconds = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
            throw new RateLimitedException(policy.name(), seconds);
        }
    }

    /** Zera o balde da chave (por exemplo, depois de um login bem-sucedido). */
    public void reset(Policy policy, String key) {
        buckets.remove(policy.name() + ":" + key);
    }

    /** Esvazia todos os baldes; usado quando os dados da instância são zerados (testes de integração). */
    public void clear() {
        buckets.clear();
    }

    @Scheduled(fixedDelay = 600_000)
    void evictIdleBuckets() {
        long threshold = clock.millis() - Duration.ofHours(1).toMillis();
        buckets.values().removeIf(entry -> entry.lastAccessMillis < threshold);
    }

    private static final class Entry {
        private final Bucket bucket;
        private volatile long lastAccessMillis;

        private Entry(Bucket bucket) {
            this.bucket = bucket;
        }
    }
}
