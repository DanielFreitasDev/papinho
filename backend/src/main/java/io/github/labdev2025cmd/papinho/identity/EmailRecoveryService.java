package io.github.labdev2025cmd.papinho.identity;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.labdev2025cmd.papinho.shared.error.ApiException;
import io.github.labdev2025cmd.papinho.shared.security.RateLimiter;

/**
 * E-mail opcional de recuperação (ID-05), desligado por padrão. O endereço só vale depois de confirmado por um
 * link; o pedido de redefinição é por {@code @usuario} e sempre tem a mesma resposta, exista ou não a conta.
 *
 * <p>Redefinir a senha por e-mail não desliga o TOTP: quem perdeu o app autenticador usa um código de
 * recuperação. Os links são de uso único e guardados só como hash.</p>
 */
@Service
class EmailRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(EmailRecoveryService.class);
    private static final String VERIFY = "VERIFY";
    private static final String RESET = "RESET";

    private final UserAccountRepository users;
    private final MailProperties properties;
    private final RecoveryMailer mailer;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    EmailRecoveryService(UserAccountRepository users, MailProperties properties, RecoveryMailer mailer,
            PasswordPolicy passwordPolicy, PasswordEncoder passwordEncoder, RateLimiter rateLimiter, JdbcTemplate jdbc,
            Clock clock) {
        this.users = users;
        this.properties = properties;
        this.mailer = mailer;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    boolean enabled() {
        return properties.enabled();
    }

    /**
     * Define o e-mail de recuperação conferindo a senha e envia o link de confirmação ao novo endereço. Um
     * endereço confirmado antes recebe o aviso da troca.
     */
    @Transactional
    void changeEmail(UUID userId, String rawEmail, String password, Locale locale) {
        requireEnabled();
        rateLimiter.consume(RateLimiter.Policy.SENSITIVE_BY_USER, userId.toString());
        UserAccount account = load(userId);
        if (!passwordEncoder.matches(password, account.passwordHash())) {
            throw new IdentityExceptions.BadCredentialsException();
        }
        String email = rawEmail.strip();
        String previous = account.verifiedEmail();
        account.changeEmail(email);
        deleteTokens(userId);
        String token = createToken(userId, VERIFY, email, properties.verifyValidity());
        String username = account.username().value();
        mailer.sendAfterCommit(userId, "verify", email, locale, "@" + username,
                properties.link("/verify-email", token), properties.verifyValidity().toHours());
        if (previous != null && !previous.equalsIgnoreCase(email)) {
            mailer.sendAfterCommit(userId, "changed", previous, locale, "@" + username);
        }
        log.info("Recovery email changed userId={}", userId);
    }

    /** Remove o e-mail de recuperação; vale mesmo com o e-mail desligado na instância. */
    @Transactional
    void removeEmail(UUID userId, String password) {
        rateLimiter.consume(RateLimiter.Policy.SENSITIVE_BY_USER, userId.toString());
        UserAccount account = load(userId);
        if (!passwordEncoder.matches(password, account.passwordHash())) {
            throw new IdentityExceptions.BadCredentialsException();
        }
        account.changeEmail(null);
        deleteTokens(userId);
        log.info("Recovery email removed userId={}", userId);
    }

    /** Confirma o endereço pelo link; público, porque o link pode ser aberto em outro aparelho. */
    @Transactional
    void verify(String token, String clientKey) {
        rateLimiter.consume(RateLimiter.Policy.RECOVERY_BY_IP, clientKey);
        Instant now = clock.instant();
        EmailToken found = consume(token, VERIFY, now);
        UserAccount account = load(found.userId());
        if (!found.email().equals(account.email())) {
            throw new IdentityExceptions.InvalidCodeException();
        }
        account.verifyEmail(now);
        log.info("Recovery email verified userId={}", found.userId());
    }

    /**
     * Pede o link de redefinição. Sempre termina sem erro (salvo limite de tentativas): só envia quando a conta
     * existe, está ativa e tem e-mail confirmado, sem revelar nada disso a quem pediu.
     */
    @Transactional
    void requestReset(String rawUsername, String clientKey, Locale locale) {
        requireEnabled();
        rateLimiter.consume(RateLimiter.Policy.RECOVERY_BY_IP, clientKey);
        String normalized = Username.normalize(rawUsername);
        rateLimiter.consume(RateLimiter.Policy.EMAIL_BY_ACCOUNT, normalized);
        Optional<UserAccount> found = users.findByUsername(normalized).filter(UserAccount::enabled);
        String email = found.map(UserAccount::verifiedEmail).orElse(null);
        if (email == null) {
            return;
        }
        UserAccount account = found.get();
        jdbc.update("delete from email_tokens where user_id = ? and purpose = ?", account.id(), RESET);
        String token = createToken(account.id(), RESET, email, properties.resetValidity());
        mailer.sendAfterCommit(account.id(), "reset", email, locale, "@" + account.username().value(),
                properties.link("/reset-password", token), properties.resetValidity().toMinutes());
    }

    /**
     * Redefine a senha pelo link (uso único, vale só para o endereço ainda confirmado). Devolve o id para o
     * chamador encerrar todas as sessões.
     */
    @Transactional
    UUID reset(String token, String newPassword, String clientKey) {
        requireEnabled();
        rateLimiter.consume(RateLimiter.Policy.RECOVERY_BY_IP, clientKey);
        EmailToken found = consume(token, RESET, clock.instant());
        UserAccount account = load(found.userId());
        String email = account.verifiedEmail();
        if (!account.enabled() || email == null || !email.equals(found.email())) {
            throw new IdentityExceptions.InvalidCodeException();
        }
        passwordPolicy.validate(newPassword);
        account.changePassword(passwordEncoder.encode(newPassword));
        jdbc.update("delete from email_tokens where user_id = ? and purpose = ?", account.id(), RESET);
        log.info("Password reset by email userId={}", account.id());
        return account.id();
    }

    private EmailToken consume(String token, String purpose, Instant now) {
        List<EmailToken> rows = jdbc.query("""
                update email_tokens set used_at = ?
                 where token_hash = ? and purpose = ? and used_at is null and expires_at > ?
                returning user_id, email
                """, (rs, row) -> new EmailToken(rs.getObject("user_id", UUID.class), rs.getString("email")),
                utc(now), InviteService.hash(token), purpose, utc(now));
        if (rows.isEmpty()) {
            throw new IdentityExceptions.InvalidCodeException();
        }
        return rows.getFirst();
    }

    private String createToken(UUID userId, String purpose, String email, Duration validity) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        jdbc.update("""
                insert into email_tokens (token_hash, user_id, purpose, email, created_at, expires_at)
                values (?, ?, ?, ?, ?, ?)
                """, InviteService.hash(token), userId, purpose, email, utc(now),
                utc(now.plus(validity)));
        return token;
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private void deleteTokens(UUID userId) {
        jdbc.update("delete from email_tokens where user_id = ?", userId);
    }

    private void requireEnabled() {
        if (!properties.enabled()) {
            throw new EmailDisabledException();
        }
    }

    private UserAccount load(UUID userId) {
        return users.findById(userId).orElseThrow(IdentityExceptions.NotFoundException::new);
    }

    private record EmailToken(UUID userId, String email) {
    }

    /** A instância não tem e-mail configurado. */
    static class EmailDisabledException extends ApiException {
        EmailDisabledException() {
            super(HttpStatus.SERVICE_UNAVAILABLE, "email-disabled", "Recovery email is disabled");
        }
    }
}
