package io.github.labdev2025cmd.papinho.privacy;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import io.github.labdev2025cmd.papinho.privacy.PrivacyExceptions.ConversationNotFoundException;
import io.github.labdev2025cmd.papinho.privacy.PrivacyExceptions.InvalidPrivacyException;
import io.github.labdev2025cmd.papinho.privacy.PrivacyExceptions.WrongLockCodeException;
import io.github.labdev2025cmd.papinho.shared.id.Uuids;
import io.github.labdev2025cmd.papinho.shared.security.RateLimiter;

/**
 * Conversas trancadas (PV-03). Cada conversa trancada aponta para um código (hash Argon2); digitar o mesmo código
 * ao trancar outra conversa reaproveita o código, que passa a ser compartilhado. As conversas trancadas saem da
 * lista e a API só devolve o conteúdo delas com um token de desbloqueio válido no cabeçalho
 * {@value #HEADER}: o token cobre os códigos digitados, vale {@link #IDLE} desde o último uso e no máximo
 * {@link #MAX_LIFETIME}, e fica só na memória (reiniciar o servidor tranca tudo de novo).
 */
@Service
public class ConversationLocks {

    public static final String HEADER = "X-Papinho-Unlock";
    static final Duration IDLE = Duration.ofMinutes(10);
    static final Duration MAX_LIFETIME = Duration.ofHours(1);
    private static final int MIN_CODE = 4;
    private static final int MAX_CODE = 64;

    private final Map<String, Grant> grants = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final NamedParameterJdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final ConversationMembership membership;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    ConversationLocks(NamedParameterJdbcTemplate jdbc, PasswordEncoder encoder, ConversationMembership membership,
            RateLimiter rateLimiter, Clock clock) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.membership = membership;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    /**
     * Tranca a conversa com o código digitado: se ele já for um dos códigos de {@code userId}, a conversa entra
     * nesse código; senão, um código novo é criado. Trancar de novo troca o código.
     */
    @Transactional
    public Unlocked lock(UUID userId, UUID conversationId, String code) {
        if (!membership.isMember(userId, conversationId)) {
            throw new ConversationNotFoundException();
        }
        if (code.length() < MIN_CODE || code.length() > MAX_CODE || code.isBlank()) {
            throw new InvalidPrivacyException("lock code");
        }
        if (hidden(userId, conversationId)) {
            // Trocar o código de uma conversa trancada exige tê-la aberto com o código atual.
            throw new WrongLockCodeException();
        }
        rateLimiter.consume(RateLimiter.Policy.UNLOCK_BY_USER, "chat:" + userId);
        UUID codeId = matchingCodes(userId, code).stream().findFirst().orElseGet(() -> {
            UUID id = Uuids.v7();
            jdbc.update("insert into lock_codes (id, user_id, code_hash, created_at) values (:id, :userId, :hash, :now)",
                    new MapSqlParameterSource("id", id).addValue("userId", userId)
                            .addValue("hash", encoder.encode(code)).addValue("now", now()));
            return id;
        });
        jdbc.update("""
                insert into locked_conversations (user_id, conversation_id, code_id, locked_at)
                values (:userId, :conversationId, :codeId, :now)
                on conflict (user_id, conversation_id) do update set code_id = excluded.code_id, locked_at = excluded.locked_at
                """, new MapSqlParameterSource("userId", userId).addValue("conversationId", conversationId)
                .addValue("codeId", codeId).addValue("now", now()));
        deleteUnusedCodes(userId);
        // Quem acabou de trancar continua com a conversa aberta: o token já cobre o código.
        return issue(userId, Set.of(codeId));
    }

    /** Destranca de vez (a conversa volta para a lista); exige um token que cubra a conversa. */
    @Transactional
    public void unlockPermanently(UUID userId, UUID conversationId) {
        if (!isLocked(userId, conversationId)) {
            return;
        }
        if (hidden(userId, conversationId)) {
            throw new WrongLockCodeException();
        }
        jdbc.update("delete from locked_conversations where user_id = :userId and conversation_id = :conversationId",
                new MapSqlParameterSource("userId", userId).addValue("conversationId", conversationId));
        deleteUnusedCodes(userId);
    }

    /**
     * Confere o código e devolve um token que cobre as conversas desse código, somado ao que o token atual da
     * requisição já cobria (abrir a pasta com dois códigos diferentes).
     */
    @Transactional(readOnly = true)
    public Unlocked unlock(UUID userId, String code) {
        String key = "chat:" + userId;
        rateLimiter.consume(RateLimiter.Policy.UNLOCK_BY_USER, key);
        List<UUID> matching = code.length() > MAX_CODE ? List.of() : matchingCodes(userId, code);
        if (matching.isEmpty()) {
            throw new WrongLockCodeException();
        }
        rateLimiter.reset(RateLimiter.Policy.UNLOCK_BY_USER, key);
        Set<UUID> codes = new HashSet<>(matching);
        Grant current = currentGrant(userId);
        if (current != null) {
            codes.addAll(current.codes());
        }
        return issue(userId, codes);
    }

    /** Conversas trancadas de {@code userId}. */
    @Transactional(readOnly = true)
    public Set<UUID> lockedConversations(UUID userId) {
        return new HashSet<>(jdbc.queryForList("select conversation_id from locked_conversations where user_id = :userId",
                new MapSqlParameterSource("userId", userId), UUID.class));
    }

    @Transactional(readOnly = true)
    public boolean isLocked(UUID userId, UUID conversationId) {
        Integer count = jdbc.queryForObject("select count(*) from locked_conversations where user_id = :userId "
                + "and conversation_id = :conversationId", new MapSqlParameterSource("userId", userId)
                .addValue("conversationId", conversationId), Integer.class);
        return count != null && count > 0;
    }

    /** Conversas trancadas que o token da requisição atual abre. */
    @Transactional(readOnly = true)
    public Set<UUID> unlockedConversations(UUID userId) {
        Grant grant = currentGrant(userId);
        if (grant == null || grant.codes().isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.queryForList("select conversation_id from locked_conversations "
                + "where user_id = :userId and code_id in (:codes)", new MapSqlParameterSource("userId", userId)
                .addValue("codes", grant.codes()), UUID.class));
    }

    /** A conversa está trancada para {@code userId} e o token da requisição atual não a abre? */
    @Transactional(readOnly = true)
    public boolean hidden(UUID userId, UUID conversationId) {
        List<UUID> code = jdbc.queryForList("select code_id from locked_conversations where user_id = :userId "
                + "and conversation_id = :conversationId", new MapSqlParameterSource("userId", userId)
                .addValue("conversationId", conversationId), UUID.class);
        if (code.isEmpty()) {
            return false;
        }
        Grant grant = currentGrant(userId);
        return grant == null || !grant.codes().contains(code.getFirst());
    }

    /** Conversas trancadas e fechadas para a requisição atual (as que somem de listas e buscas). */
    @Transactional(readOnly = true)
    public Set<UUID> hiddenConversations(UUID userId) {
        Set<UUID> locked = lockedConversations(userId);
        if (!locked.isEmpty()) {
            locked.removeAll(unlockedConversations(userId));
        }
        return locked;
    }

    /** Esquece o token da requisição atual (trancar a pasta de novo). */
    public void forget() {
        String token = requestToken();
        if (token != null) {
            grants.remove(hash(token));
        }
    }

    @Scheduled(fixedDelay = 300_000)
    void evictExpired() {
        Instant now = clock.instant();
        grants.values().removeIf(grant -> grant.expired(now));
    }

    private Unlocked issue(UUID userId, Set<UUID> codes) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        Grant grant = new Grant(userId, Set.copyOf(codes), now.plus(MAX_LIFETIME), new java.util.concurrent.atomic
                .AtomicReference<>(now.plus(IDLE)));
        grants.put(hash(token), grant);
        return new Unlocked(token, grant.expiresAt(now));
    }

    private @Nullable Grant currentGrant(UUID userId) {
        String token = requestToken();
        if (token == null) {
            return null;
        }
        Grant grant = grants.get(hash(token));
        Instant now = clock.instant();
        if (grant == null || !grant.userId().equals(userId) || grant.expired(now)) {
            return null;
        }
        grant.touch(now);
        return grant;
    }

    private List<UUID> matchingCodes(UUID userId, String code) {
        return jdbc.query("select id, code_hash from lock_codes where user_id = :userId order by created_at",
                new MapSqlParameterSource("userId", userId), (rs, index) -> new String[] {rs.getString("id"),
                        rs.getString("code_hash")})
                .stream().filter(row -> encoder.matches(code, row[1])).map(row -> UUID.fromString(row[0])).toList();
    }

    private void deleteUnusedCodes(UUID userId) {
        jdbc.update("delete from lock_codes c where c.user_id = :userId and not exists "
                + "(select 1 from locked_conversations l where l.code_id = c.id)",
                new MapSqlParameterSource("userId", userId));
    }

    private java.time.OffsetDateTime now() {
        return clock.instant().atOffset(ZoneOffset.UTC);
    }

    private static @Nullable String requestToken() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            String value = attributes.getRequest().getHeader(HEADER);
            return value == null || value.isBlank() ? null : value.strip();
        }
        return null;
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(
                    java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record Grant(UUID userId, Set<UUID> codes, Instant hardLimit,
            java.util.concurrent.atomic.AtomicReference<Instant> idleLimit) {

        boolean expired(Instant now) {
            return !now.isBefore(hardLimit) || !now.isBefore(idleLimit.get());
        }

        void touch(Instant now) {
            idleLimit.set(now.plus(IDLE));
        }

        Instant expiresAt(Instant now) {
            Instant idle = idleLimit.get();
            return idle.isBefore(hardLimit) ? idle : hardLimit;
        }
    }

    /** Token de desbloqueio e até quando vale se não for usado. */
    public record Unlocked(String token, Instant expiresAt) {
    }
}
