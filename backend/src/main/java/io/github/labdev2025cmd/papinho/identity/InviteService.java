package io.github.labdev2025cmd.papinho.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.labdev2025cmd.papinho.admin.InstanceSettingsService;
import io.github.labdev2025cmd.papinho.admin.RegistrationMode;
import io.github.labdev2025cmd.papinho.shared.security.RateLimiter;

/** Convites para criar conta (ID-07): link e QR Code com expiração e limite de usos. */
@Service
public class InviteService {

    static final Duration MAX_VALIDITY = Duration.ofDays(30);
    static final int MAX_USES = 100;

    private final InviteRepository repository;
    private final UserAccountRepository users;
    private final InstanceSettingsService settings;
    private final RateLimiter rateLimiter;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    InviteService(InviteRepository repository, UserAccountRepository users, InstanceSettingsService settings,
            RateLimiter rateLimiter, Clock clock) {
        this.repository = repository;
        this.users = users;
        this.settings = settings;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    /**
     * Cria um convite. Com a instância fechada só o admin convida (o convite reabre a porta para uma pessoa).
     *
     * @param validity validade, de 1 hora a 30 dias
     * @param maxUses usos permitidos, de 1 a 100
     */
    @Transactional
    public CreatedInvite create(UUID creator, Duration validity, int maxUses) {
        rateLimiter.consume(RateLimiter.Policy.INVITE_CREATE_BY_USER, creator.toString());
        UserAccount account = users.findById(creator).orElseThrow(IdentityExceptions.NotFoundException::new);
        if (settings.current().registrationMode() == RegistrationMode.CLOSED && account.role() != Role.ADMIN) {
            throw new IdentityExceptions.RegistrationClosedException();
        }
        if (validity.compareTo(Duration.ofHours(1)) < 0 || validity.compareTo(MAX_VALIDITY) > 0
                || maxUses < 1 || maxUses > MAX_USES) {
            throw new IllegalArgumentException("Invite limits out of range");
        }
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        Invite invite = repository.save(new Invite(hash(token), creator, now, now.plus(validity), maxUses));
        return new CreatedInvite(toView(invite, now), token);
    }

    /** Convites criados pelo usuário, do mais novo para o mais antigo. */
    @Transactional(readOnly = true)
    public List<InviteView> list(UUID creator) {
        Instant now = clock.instant();
        return repository.findTop50ByCreatedByOrderByCreatedAtDesc(creator).stream().map(i -> toView(i, now))
                .toList();
    }

    /** Revoga um convite do próprio usuário; o de outra pessoa responde 404 (IDOR). */
    @Transactional
    public void revoke(UUID creator, UUID inviteId) {
        repository.findByIdAndCreatedBy(inviteId, creator).orElseThrow(IdentityExceptions.NotFoundException::new)
                .revoke(clock.instant());
    }

    /** O convite ainda pode ser usado? Público, com limite por IP contra adivinhação. */
    @Transactional(readOnly = true)
    public boolean isUsable(String token, String clientKey) {
        rateLimiter.consume(RateLimiter.Policy.INVITE_CHECK_BY_IP, clientKey);
        return repository.findByTokenHash(hash(token))
                .map(i -> i.usable(clock.instant()) && opensClosedInstance(i)).orElse(false);
    }

    /**
     * Consome um uso dentro da transação do cadastro, com lock de linha contra corrida. Com a instância fechada,
     * só o convite de quem é admin agora serve (ID-07).
     */
    void consume(String token) {
        Instant now = clock.instant();
        Invite invite = repository.lockByTokenHash(hash(token)).filter(i -> i.usable(now))
                .orElseThrow(IdentityExceptions.InviteInvalidException::new);
        if (!opensClosedInstance(invite)) {
            throw new IdentityExceptions.RegistrationClosedException();
        }
        invite.consume(now);
    }

    private boolean opensClosedInstance(Invite invite) {
        return settings.current().registrationMode() != RegistrationMode.CLOSED || users.findById(invite.createdBy())
                .map(creator -> creator.role() == Role.ADMIN && creator.enabled()).orElse(false);
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static InviteView toView(Invite invite, Instant now) {
        return new InviteView(invite.id(), invite.createdAt(), invite.expiresAt(), invite.maxUses(), invite.uses(),
                invite.revoked(), invite.usable(now));
    }

    /** Convite como mostrado ao dono (sem o token, que só aparece na criação). */
    public record InviteView(UUID id, Instant createdAt, Instant expiresAt, int maxUses, int uses, boolean revoked,
            boolean usable) {
    }

    /** Convite recém-criado com o token em texto puro. */
    public record CreatedInvite(InviteView invite, String token) {
    }
}
