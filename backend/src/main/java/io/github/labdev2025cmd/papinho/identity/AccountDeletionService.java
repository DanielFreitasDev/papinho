package io.github.labdev2025cmd.papinho.identity;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.labdev2025cmd.papinho.profile.ProfileService;
import io.github.labdev2025cmd.papinho.shared.error.ApiException;
import io.github.labdev2025cmd.papinho.shared.security.RateLimiter;

/**
 * Excluir a própria conta (BK-02, LGPD). Exige a senha. Some tudo o que é só da pessoa (perfil e foto, contatos,
 * preferências, passkeys, sessões, conversas da lista dela); as mensagens que ela mandou continuam com quem
 * recebeu, como "Conta apagada". O último admin não sai enquanto houver outras pessoas na instância.
 */
@Service
public class AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);

    private final UserAccountRepository users;
    private final ProfileService profiles;
    private final SessionService sessions;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;
    private final ApplicationEventPublisher events;
    private final JdbcTemplate jdbc;

    AccountDeletionService(UserAccountRepository users, ProfileService profiles, SessionService sessions,
            PasswordEncoder passwordEncoder, RateLimiter rateLimiter, ApplicationEventPublisher events,
            JdbcTemplate jdbc) {
        this.users = users;
        this.profiles = profiles;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.events = events;
        this.jdbc = jdbc;
    }

    @Transactional
    public void delete(UUID userId, String password) {
        rateLimiter.consume(RateLimiter.Policy.SENSITIVE_BY_USER, userId.toString());
        UserAccount account = users.findById(userId).orElseThrow(IdentityExceptions.NotFoundException::new);
        if (!passwordEncoder.matches(password, account.passwordHash())) {
            throw new IdentityExceptions.BadCredentialsException();
        }
        if (account.role() == Role.ADMIN) {
            Integer otherAdmins = jdbc.queryForObject(
                    "select count(*) from user_accounts where role = 'ADMIN' and enabled and id <> ?", Integer.class,
                    userId);
            Integer others = jdbc.queryForObject("select count(*) from user_accounts where id <> ?", Integer.class,
                    userId);
            if (otherAdmins != null && otherAdmins == 0 && others != null && others > 0) {
                throw new LastAdminException();
            }
        }
        events.publishEvent(new AccountDeleting(userId));
        if (profiles.own(userId).hasPhoto()) {
            profiles.removePhoto(userId);
        }
        // Passkeys ficam nas tabelas do Spring Security, ligadas pelo id da conta como nome do usuário.
        jdbc.update("delete from user_entities where name = ?", userId.toString());
        users.delete(account);
        users.flush();
        sessions.revokeAllExcept(userId, null);
        log.info("Account deleted by its owner userId={}", userId);
    }

    static class LastAdminException extends ApiException {
        LastAdminException() {
            super(HttpStatus.CONFLICT, "account-last-admin", "The last admin cannot delete the account");
        }
    }
}
