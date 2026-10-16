package io.github.labdev2025cmd.papinho.identity;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.labdev2025cmd.papinho.profile.ProfileService;
import io.github.labdev2025cmd.papinho.profile.ProfileView;
import io.github.labdev2025cmd.papinho.shared.error.ApiException;

/** Gestão de usuários pelo admin (AD-01): listar, desativar, reativar, redefinir senha e tornar admin. */
@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);
    private static final String PASSWORD_ALPHABET = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    /** Advisory lock das ações que tiram poder de admin: dois admins não se tiram ao mesmo tempo. */
    static final long ADMIN_LOCK = 0x5041_5049_4E48_4F02L;

    private final UserAccountRepository users;
    private final ProfileService profiles;
    private final SessionService sessions;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();

    AdminUserService(UserAccountRepository users, ProfileService profiles, SessionService sessions,
            PasswordEncoder passwordEncoder, JdbcTemplate jdbc) {
        this.users = users;
        this.profiles = profiles;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Page<AdminUserView> list(UUID adminId, String query, int page, int size) {
        Page<UserAccount> accounts = users.search(Username.normalize(query), PageRequest.of(page, size));
        Map<UUID, ProfileView> views = profiles.viewAll(adminId, accounts.map(UserAccount::id).getContent());
        return accounts.map(account -> new AdminUserView(account.id(), account.username().value(),
                views.containsKey(account.id()) ? views.get(account.id()).displayName() : "",
                account.role() == Role.ADMIN, account.enabled(), account.createdAt()));
    }

    /**
     * Torna alguém admin da instância ou tira (AD-01). O admin não muda o próprio papel. As sessões da pessoa são
     * encerradas para o papel novo valer no próximo login.
     */
    @Transactional
    public void changeAdmin(UUID adminId, UUID userId, boolean admin) {
        if (adminId.equals(userId)) {
            throw new SelfActionException();
        }
        requireActiveAdmin(adminId);
        load(userId).changeRole(admin ? Role.ADMIN : Role.USER);
        sessions.revokeAllExcept(userId, null);
        log.info("User role changed by admin userId={} adminId={} admin={}", userId, adminId, admin);
    }

    /** Desativa a conta e encerra todas as sessões dela. O admin não pode desativar a si mesmo. */
    @Transactional
    public void disable(UUID adminId, UUID userId) {
        if (adminId.equals(userId)) {
            throw new SelfActionException();
        }
        requireActiveAdmin(adminId);
        load(userId).disable();
        sessions.revokeAllExcept(userId, null);
        log.info("User disabled by admin userId={} adminId={}", userId, adminId);
    }

    @Transactional
    public void enable(UUID adminId, UUID userId) {
        load(userId).enable();
        log.info("User enabled by admin userId={} adminId={}", userId, adminId);
    }

    /**
     * Gera uma senha temporária para a pessoa entrar e trocar; encerra as sessões existentes.
     * A senha aparece só nesta resposta.
     */
    @Transactional
    public String resetPassword(UUID adminId, UUID userId) {
        StringBuilder password = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            password.append(PASSWORD_ALPHABET.charAt(random.nextInt(PASSWORD_ALPHABET.length())));
        }
        load(userId).changePassword(passwordEncoder.encode(password));
        sessions.revokeAllExcept(userId, null);
        log.info("Password reset by admin userId={} adminId={}", userId, adminId);
        return password.toString();
    }

    /**
     * Ninguém tira o próprio poder, mas dois admins podiam tirar um ao outro ao mesmo tempo e deixar a instância sem
     * admin. Com a trava, a segunda ação vê que quem age já não é admin ativo e é recusada.
     */
    private void requireActiveAdmin(UUID adminId) {
        jdbc.queryForObject("select pg_advisory_xact_lock(?)", Object.class, ADMIN_LOCK);
        UserAccount acting = load(adminId);
        if (acting.role() != Role.ADMIN || !acting.enabled()) {
            throw new NotActiveAdminException();
        }
    }

    private UserAccount load(UUID userId) {
        return users.findById(userId).orElseThrow(IdentityExceptions.NotFoundException::new);
    }

    /** Usuário como listado no painel do admin. */
    public record AdminUserView(UUID id, String username, String displayName, boolean admin, boolean enabled,
            Instant createdAt) {
    }

    static class NotActiveAdminException extends ApiException {
        NotActiveAdminException() {
            super(HttpStatus.FORBIDDEN, "forbidden", "Acting admin is no longer an active admin");
        }
    }

    static class SelfActionException extends ApiException {
        SelfActionException() {
            super(HttpStatus.CONFLICT, "admin-self-action", "Admin cannot disable own account");
        }
    }
}
