package io.github.labdev2025cmd.papinho.privacy;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.labdev2025cmd.papinho.privacy.PrivacyExceptions.InvalidPrivacyException;
import io.github.labdev2025cmd.papinho.privacy.PrivacyExceptions.ScreenLockMissingException;
import io.github.labdev2025cmd.papinho.privacy.PrivacyExceptions.WrongScreenSecretException;
import io.github.labdev2025cmd.papinho.shared.security.RateLimiter;

/**
 * Bloqueio de tela (PV-02): um PIN ou senha próprios, separados da senha da conta, com hash Argon2, e os gatilhos
 * (inatividade, trocar de aba, atalho), todos desligados por padrão. É uma proteção visual: a sessão continua
 * ativa e o cliente é quem cobre a tela; o servidor só confere o segredo, com limite de tentativas (SG-03).
 */
@Service
public class ScreenLockService {

    private static final Pattern PIN = Pattern.compile("\\d{4,12}");
    /** Atalho com pelo menos um modificador e uma tecla: {@code Alt+Shift+L}, {@code Ctrl+Alt+9}. */
    private static final Pattern SHORTCUT = Pattern.compile("((Ctrl|Alt|Shift|Meta)\\+){1,3}[A-Z0-9]");

    enum Kind {
        PIN, PASSWORD
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    ScreenLockService(NamedParameterJdbcTemplate jdbc, PasswordEncoder encoder, RateLimiter rateLimiter, Clock clock) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    /** Configuração do bloqueio, sem o hash; {@code enabled} falso quando não há segredo. */
    @Transactional(readOnly = true)
    public ScreenLockView view(UUID userId) {
        return find(userId).map(Row::view).orElse(new ScreenLockView(false, null, null, false, null, false));
    }

    /**
     * Cria ou altera o bloqueio. Trocar o segredo de um bloqueio que já existe exige o segredo atual; mudar só os
     * gatilhos, não (quem está com a tela desbloqueada já tem acesso a tudo).
     */
    @Transactional
    public ScreenLockView save(UUID userId, ScreenLockChange change) {
        Optional<Row> current = find(userId);
        if (change.idleMinutes() != null && (change.idleMinutes() < 1 || change.idleMinutes() > 1440)) {
            throw new InvalidPrivacyException("idle minutes");
        }
        if (change.shortcut() != null && !SHORTCUT.matcher(change.shortcut()).matches()) {
            throw new InvalidPrivacyException("shortcut");
        }
        String hash;
        Kind kind;
        if (change.secret() != null) {
            if (current.isPresent()) {
                requireSecret(userId, current.get(), change.currentSecret());
            }
            kind = change.kind() == null ? Kind.PIN : change.kind();
            validateSecret(kind, change.secret());
            hash = encoder.encode(change.secret());
        } else {
            Row row = current.orElseThrow(() -> new InvalidPrivacyException("secret required"));
            hash = row.hash();
            kind = row.kind();
        }
        jdbc.update("""
                insert into screen_locks (user_id, secret_kind, secret_hash, idle_minutes, lock_on_hidden, shortcut,
                       allow_passkey, updated_at)
                values (:userId, :kind, :hash, :idle, :hidden, :shortcut, :passkey, :now)
                on conflict (user_id) do update set secret_kind = excluded.secret_kind,
                       secret_hash = excluded.secret_hash, idle_minutes = excluded.idle_minutes,
                       lock_on_hidden = excluded.lock_on_hidden, shortcut = excluded.shortcut,
                       allow_passkey = excluded.allow_passkey, updated_at = excluded.updated_at
                """, new MapSqlParameterSource("userId", userId).addValue("kind", kind.name()).addValue("hash", hash)
                .addValue("idle", change.idleMinutes()).addValue("hidden", change.lockOnHidden())
                .addValue("shortcut", change.shortcut()).addValue("passkey", change.allowPasskey())
                .addValue("now", clock.instant().atOffset(ZoneOffset.UTC)));
        return view(userId);
    }

    /** Desliga o bloqueio; exige o segredo atual. */
    @Transactional
    public void remove(UUID userId, @Nullable String currentSecret) {
        Row row = find(userId).orElseThrow(ScreenLockMissingException::new);
        requireSecret(userId, row, currentSecret);
        jdbc.update("delete from screen_locks where user_id = :userId", new MapSqlParameterSource("userId", userId));
    }

    /** Confere o segredo para desbloquear a tela; erros contam no limite de tentativas. */
    @Transactional(readOnly = true)
    public void unlock(UUID userId, @Nullable String secret) {
        Row row = find(userId).orElseThrow(ScreenLockMissingException::new);
        requireSecret(userId, row, secret);
    }

    private void requireSecret(UUID userId, Row row, @Nullable String secret) {
        String key = "screen:" + userId;
        rateLimiter.consume(RateLimiter.Policy.UNLOCK_BY_USER, key);
        if (secret == null || !encoder.matches(secret, row.hash())) {
            throw new WrongScreenSecretException();
        }
        rateLimiter.reset(RateLimiter.Policy.UNLOCK_BY_USER, key);
    }

    private static void validateSecret(Kind kind, String secret) {
        boolean valid = kind == Kind.PIN ? PIN.matcher(secret).matches()
                : secret.length() >= 6 && secret.length() <= 128 && !secret.isBlank();
        if (!valid) {
            throw new InvalidPrivacyException("secret");
        }
    }

    private Optional<Row> find(UUID userId) {
        List<Row> rows = jdbc.query("select * from screen_locks where user_id = :userId",
                new MapSqlParameterSource("userId", userId), (rs, index) -> {
                    int idle = rs.getInt("idle_minutes");
                    Integer idleMinutes = rs.wasNull() ? null : idle;
                    return new Row(Kind.valueOf(rs.getString("secret_kind")), rs.getString("secret_hash"),
                            idleMinutes, rs.getBoolean("lock_on_hidden"), rs.getString("shortcut"),
                            rs.getBoolean("allow_passkey"));
                });
        return rows.stream().findFirst();
    }

    private record Row(Kind kind, String hash, @Nullable Integer idleMinutes, boolean lockOnHidden,
            @Nullable String shortcut, boolean allowPasskey) {

        ScreenLockView view() {
            return new ScreenLockView(true, kind, idleMinutes, lockOnHidden, shortcut, allowPasskey);
        }
    }

    /**
     * Bloqueio de tela como o cliente vê.
     *
     * @param idleMinutes bloquear depois de tantos minutos sem uso; nulo = desligado
     * @param lockOnHidden bloquear ao trocar de aba ou janela
     * @param shortcut atalho de teclado que bloqueia, como {@code Alt+Shift+L}; nulo = desligado
     * @param allowPasskey desbloquear também com passkey da conta
     */
    public record ScreenLockView(boolean enabled, @Nullable Kind kind, @Nullable Integer idleMinutes,
            boolean lockOnHidden, @Nullable String shortcut, boolean allowPasskey) {
    }

    /** @param secret novo PIN ou senha; nulo para manter o atual */
    public record ScreenLockChange(@Nullable Kind kind, @Nullable String secret, @Nullable String currentSecret,
            @Nullable Integer idleMinutes, boolean lockOnHidden, @Nullable String shortcut, boolean allowPasskey) {
    }
}
