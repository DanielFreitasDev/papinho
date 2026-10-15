package io.github.labdev2025cmd.papinho.identity;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.labdev2025cmd.papinho.admin.InstanceSettings;
import io.github.labdev2025cmd.papinho.admin.InstanceSettingsService;
import io.github.labdev2025cmd.papinho.admin.RegistrationMode;
import io.github.labdev2025cmd.papinho.profile.ProfileService;
import io.github.labdev2025cmd.papinho.shared.crypto.SecretCipher;
import io.github.labdev2025cmd.papinho.shared.qr.QrCodes;
import io.github.labdev2025cmd.papinho.shared.security.PapinhoUser;
import io.github.labdev2025cmd.papinho.shared.security.RateLimiter;

/**
 * Casos de uso da conta: cadastro, autenticação, troca de {@code @usuario} e de senha, TOTP e códigos de
 * recuperação (ID-01 a ID-05, ID-08).
 */
@Service
public class IdentityService {

    private static final Logger log = LoggerFactory.getLogger(IdentityService.class);

    /** Quantidade de códigos de recuperação gerados de cada vez (ID-05). */
    static final int RECOVERY_CODE_COUNT = 10;
    /** Período em que o {@code @usuario} antigo fica reservado ao dono depois de uma troca (ID-03). */
    static final Duration USERNAME_RESERVATION = Duration.ofDays(30);
    /** Chave do advisory lock que serializa cadastros: decide sem corrida quem é o primeiro usuário (ID-08). */
    private static final long REGISTRATION_LOCK = 0x5041_5049_4E48_4F01L;
    private static final String RECOVERY_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";

    private final UserAccountRepository users;
    private final UsernameReservationRepository reservations;
    private final RecoveryCodeRepository recoveryCodes;
    private final InviteService invites;
    private final ProfileService profiles;
    private final InstanceSettingsService settings;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final SecretCipher cipher;
    private final RateLimiter rateLimiter;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final String dummyHash;

    IdentityService(UserAccountRepository users, UsernameReservationRepository reservations,
            RecoveryCodeRepository recoveryCodes, InviteService invites, ProfileService profiles,
            InstanceSettingsService settings, PasswordPolicy passwordPolicy, PasswordEncoder passwordEncoder,
            SecretCipher cipher, RateLimiter rateLimiter, JdbcTemplate jdbc, Clock clock) {
        this.users = users;
        this.reservations = reservations;
        this.recoveryCodes = recoveryCodes;
        this.invites = invites;
        this.profiles = profiles;
        this.settings = settings;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.cipher = cipher;
        this.rateLimiter = rateLimiter;
        this.jdbc = jdbc;
        this.clock = clock;
        // Hash de referência para gastar o mesmo tempo quando o usuário não existe (não revela existência).
        this.dummyHash = passwordEncoder.encode("papinho-timing-equalizer");
    }

    /**
     * Cria uma conta. O primeiro usuário da instância vira admin e dispensa convite (ID-08); depois, vale o
     * modo de cadastro (ID-07). Devolve os códigos de recuperação em texto puro, mostrados uma única vez.
     */
    @Transactional
    public Registration register(String rawUsername, String displayName, String password, @Nullable String inviteToken,
            String clientKey) {
        rateLimiter.consume(RateLimiter.Policy.REGISTER_BY_IP, clientKey);
        Username username = Username.parse(rawUsername);
        passwordPolicy.validate(password);

        jdbc.queryForObject("select pg_advisory_xact_lock(?)", Object.class, REGISTRATION_LOCK);
        boolean firstUser = users.count() == 0;
        if (!firstUser) {
            RegistrationMode mode = settings.current().registrationMode();
            if (mode == RegistrationMode.CLOSED) {
                throw new IdentityExceptions.RegistrationClosedException();
            }
            if (mode == RegistrationMode.INVITE_ONLY || inviteToken != null) {
                if (inviteToken == null || inviteToken.isBlank()) {
                    throw new IdentityExceptions.InviteInvalidException();
                }
                invites.consume(inviteToken);
            }
        }
        Instant now = clock.instant();
        ensureAvailable(username, null, now);

        UserAccount account = users.saveAndFlush(new UserAccount(username, passwordEncoder.encode(password),
                firstUser ? Role.ADMIN : Role.USER, now));
        profiles.create(account.id(), displayName);
        List<String> codes = replaceRecoveryCodes(account.id());
        log.info("Account registered userId={} admin={}", account.id(), firstUser);
        return new Registration(toPrincipal(account), codes);
    }

    /**
     * Autentica por senha (e TOTP, se ligado). Usuário inexistente, desativado ou senha errada dão a mesma
     * resposta e gastam o mesmo tempo (ID-04). Limites por IP e por conta (SG-03).
     */
    @Transactional(readOnly = true)
    public PapinhoUser authenticate(String rawUsername, String password, @Nullable String totpCode, String clientKey) {
        rateLimiter.consume(RateLimiter.Policy.LOGIN_BY_IP, clientKey);
        String normalized = Username.normalize(rawUsername);
        rateLimiter.consume(RateLimiter.Policy.LOGIN_BY_ACCOUNT, normalized);

        Optional<UserAccount> found = users.findByUsername(normalized);
        if (found.isEmpty()) {
            passwordEncoder.matches(password, dummyHash);
            throw new IdentityExceptions.BadCredentialsException();
        }
        UserAccount account = found.get();
        if (!passwordEncoder.matches(password, account.passwordHash()) || !account.enabled()) {
            throw new IdentityExceptions.BadCredentialsException();
        }
        if (account.totpEnabled()) {
            if (totpCode == null || totpCode.isBlank()) {
                throw new IdentityExceptions.TotpRequiredException();
            }
            if (!Totp.verify(cipher.decrypt(requireSecret(account)), totpCode.strip(), clock.instant())) {
                throw new IdentityExceptions.InvalidCodeException();
            }
        }
        rateLimiter.reset(RateLimiter.Policy.LOGIN_BY_ACCOUNT, normalized);
        return toPrincipal(account);
    }

    /** Dados da própria conta. */
    @Transactional(readOnly = true)
    public AccountView account(UUID userId) {
        UserAccount account = load(userId);
        Instant nextChange = nextUsernameChange(account, settings.current());
        int remaining = recoveryCodes.findUnused(userId).size();
        return new AccountView(account.id(), account.username().value(), account.role() == Role.ADMIN,
                account.totpEnabled(), remaining, account.createdAt(),
                nextChange.isAfter(clock.instant()) ? nextChange : null);
    }

    /**
     * Troca o {@code @usuario} (ID-03): respeita o cooldown configurável e reserva o nome antigo para o dono
     * por 30 dias. O dono pode voltar ao nome antigo durante a reserva.
     */
    @Transactional
    public AccountView changeUsername(UUID userId, String rawUsername) {
        UserAccount account = load(userId);
        Username target = Username.parse(rawUsername);
        if (target.equals(account.username())) {
            return account(userId);
        }
        Instant now = clock.instant();
        Instant nextChange = nextUsernameChange(account, settings.current());
        if (nextChange.isAfter(now)) {
            long days = Math.max(1, Duration.between(now, nextChange).toDays() + 1);
            throw new IdentityExceptions.UsernameCooldownException(days);
        }
        ensureAvailable(target, userId, now);
        Username previous = account.username();
        reservations.findById(target.value()).ifPresent(reservations::delete);
        reservations.flush();
        account.changeUsername(target, now);
        users.flush();
        UsernameReservation reservation = reservations.findById(previous.value())
                .orElseGet(() -> new UsernameReservation(previous, userId, now.plus(USERNAME_RESERVATION)));
        reservation.renew(userId, now.plus(USERNAME_RESERVATION));
        reservations.save(reservation);
        log.info("Username changed userId={}", userId);
        return account(userId);
    }

    /** Troca a senha conferindo a atual. O chamador encerra as outras sessões. */
    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword, String clientKey) {
        rateLimiter.consume(RateLimiter.Policy.SENSITIVE_BY_USER, userId.toString());
        UserAccount account = load(userId);
        if (!passwordEncoder.matches(currentPassword, account.passwordHash())) {
            throw new IdentityExceptions.BadCredentialsException();
        }
        passwordPolicy.validate(newPassword);
        account.changePassword(passwordEncoder.encode(newPassword));
        log.info("Password changed userId={}", userId);
    }

    /**
     * Recupera a conta com um código de recuperação (ID-05): define nova senha e desliga o TOTP (o caso típico
     * é ter perdido o celular). O código é consumido. Devolve o id para o chamador encerrar todas as sessões.
     */
    @Transactional
    public UUID recover(String rawUsername, String code, String newPassword, String clientKey) {
        rateLimiter.consume(RateLimiter.Policy.RECOVERY_BY_IP, clientKey);
        String normalized = Username.normalize(rawUsername);
        rateLimiter.consume(RateLimiter.Policy.LOGIN_BY_ACCOUNT, normalized);
        Optional<UserAccount> found = users.findByUsername(normalized);
        String cleanCode = code.strip().toLowerCase(java.util.Locale.ROOT).replace(" ", "").replace("-", "");
        RecoveryCode match = null;
        if (found.isPresent() && found.get().enabled()) {
            for (RecoveryCode candidate : recoveryCodes.findUnused(found.get().id())) {
                if (passwordEncoder.matches(cleanCode, candidate.codeHash())) {
                    match = candidate;
                    break;
                }
            }
        } else {
            passwordEncoder.matches(cleanCode, dummyHash);
        }
        if (match == null) {
            throw new IdentityExceptions.InvalidCodeException();
        }
        passwordPolicy.validate(newPassword);
        UserAccount account = found.get();
        match.markUsed(clock.instant());
        account.changePassword(passwordEncoder.encode(newPassword));
        account.disableTotp();
        log.info("Account recovered with recovery code userId={}", account.id());
        return account.id();
    }

    /** Gera novos códigos de recuperação, invalidando os anteriores; exige a senha. */
    @Transactional
    public List<String> regenerateRecoveryCodes(UUID userId, String password) {
        rateLimiter.consume(RateLimiter.Policy.SENSITIVE_BY_USER, userId.toString());
        UserAccount account = load(userId);
        if (!passwordEncoder.matches(password, account.passwordHash())) {
            throw new IdentityExceptions.BadCredentialsException();
        }
        return replaceRecoveryCodes(userId);
    }

    /** Inicia a configuração do TOTP: segredo novo (ainda não exigido no login) e QR Code. */
    @Transactional
    public TotpEnrollment startTotp(UUID userId) {
        UserAccount account = load(userId);
        String secret = Totp.newSecret(random);
        account.startTotpEnrollment(cipher.encrypt(secret));
        String uri = Totp.uri("Papinho", "@" + account.username().value(), secret);
        return new TotpEnrollment(secret, uri, QrCodes.svg(uri, "QR Code para o app autenticador"));
    }

    /** Confirma o TOTP com um código do app; a partir daqui o login exige o código. */
    @Transactional
    public void confirmTotp(UUID userId, String code) {
        rateLimiter.consume(RateLimiter.Policy.SENSITIVE_BY_USER, userId.toString());
        UserAccount account = load(userId);
        if (account.totpSecret() == null || !Totp.verify(cipher.decrypt(requireSecret(account)), code.strip(),
                clock.instant())) {
            throw new IdentityExceptions.InvalidCodeException();
        }
        account.confirmTotp();
        log.info("TOTP enabled userId={}", userId);
    }

    /** Desliga o TOTP; exige a senha. */
    @Transactional
    public void disableTotp(UUID userId, String password) {
        rateLimiter.consume(RateLimiter.Policy.SENSITIVE_BY_USER, userId.toString());
        UserAccount account = load(userId);
        if (!passwordEncoder.matches(password, account.passwordHash())) {
            throw new IdentityExceptions.BadCredentialsException();
        }
        account.disableTotp();
        log.info("TOTP disabled userId={}", userId);
    }

    /** Nenhuma conta existe ainda: o próximo cadastro cria o admin (ID-08). */
    @Transactional(readOnly = true)
    public boolean isSetupRequired() {
        return users.count() == 0;
    }

    /** O usuário ainda pode usar a sessão? (Conta existe e está ativa.) */
    @Transactional(readOnly = true)
    public boolean isActive(UUID userId) {
        return users.findById(userId).map(UserAccount::enabled).orElse(false);
    }

    /** {@code @usuario} livre: formato válido, não reservado pelo sistema nem por outra pessoa. */
    @Transactional(readOnly = true)
    public boolean isUsernameAvailable(String rawUsername, @Nullable UUID requester) {
        try {
            ensureAvailable(Username.parse(rawUsername), requester, clock.instant());
            return true;
        } catch (IdentityExceptions.UsernameUnavailableException | InvalidUsernameException exception) {
            return false;
        }
    }

    private void ensureAvailable(Username username, @Nullable UUID requester, Instant now) {
        if (ReservedUsernames.isReserved(username)) {
            throw new IdentityExceptions.UsernameUnavailableException();
        }
        Optional<UserAccount> owner = users.findByUsername(username.value());
        if (owner.isPresent() && !owner.get().id().equals(requester)) {
            throw new IdentityExceptions.UsernameUnavailableException();
        }
        UUID candidate = requester == null ? new UUID(0, 0) : requester;
        if (reservations.findById(username.value()).filter(r -> r.blocks(candidate, now)).isPresent()) {
            throw new IdentityExceptions.UsernameUnavailableException();
        }
    }

    private Instant nextUsernameChange(UserAccount account, InstanceSettings current) {
        Instant last = account.usernameChangedAt();
        return last == null ? Instant.MIN : last.plus(current.usernameChangeCooldown());
    }

    private List<String> replaceRecoveryCodes(UUID userId) {
        recoveryCodes.deleteAllByUserId(userId);
        List<String> codes = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String code = recoveryCode();
            codes.add(code);
            recoveryCodes.save(new RecoveryCode(userId, passwordEncoder.encode(code.replace("-", ""))));
        }
        return codes.stream().toList();
    }

    /** Código de 10 caracteres sem ambíguos (0/o, 1/l/i), mostrado como {@code xxxxx-xxxxx}. */
    private String recoveryCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            if (i == 5) {
                code.append('-');
            }
            code.append(RECOVERY_ALPHABET.charAt(random.nextInt(RECOVERY_ALPHABET.length())));
        }
        return code.toString();
    }

    private UserAccount load(UUID userId) {
        return users.findById(userId).orElseThrow(IdentityExceptions.NotFoundException::new);
    }

    private static String requireSecret(UserAccount account) {
        String secret = account.totpSecret();
        if (secret == null) {
            throw new IllegalStateException("TOTP secret missing");
        }
        return secret;
    }

    static PapinhoUser toPrincipal(UserAccount account) {
        return new PapinhoUser(account.id(), account.username().value(), account.role() == Role.ADMIN,
                account.enabled(), null);
    }

    /** Resultado do cadastro: principal para abrir a sessão e códigos de recuperação em texto puro. */
    public record Registration(PapinhoUser user, List<String> recoveryCodes) {
    }

    /** Segredo e QR Code para o app autenticador. */
    public record TotpEnrollment(String secret, String otpauthUri, String qrSvg) {
    }

    /**
     * Dados da conta para a tela de configurações.
     *
     * @param nextUsernameChangeAt quando a próxima troca de {@code @usuario} será permitida, ou nulo se já pode
     */
    public record AccountView(UUID id, String username, boolean admin, boolean totpEnabled,
            int remainingRecoveryCodes, Instant createdAt, @Nullable Instant nextUsernameChangeAt) {
    }
}
