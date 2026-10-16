package io.github.labdev2025cmd.papinho.identity;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.labdev2025cmd.papinho.profile.ProfileService;
import io.github.labdev2025cmd.papinho.profile.ProfileView;
import io.github.labdev2025cmd.papinho.shared.security.CurrentUser;
import io.github.labdev2025cmd.papinho.shared.security.PapinhoUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cadastro, login, recuperação (por código ou e-mail) e dados da sessão atual (ID-01, ID-04, ID-05, ID-08). */
@RestController
@RequestMapping("/api/v1")
class AuthController {

    private final IdentityService identity;
    private final ProfileService profiles;
    private final SessionLogin sessionLogin;
    private final SessionService sessions;
    private final EmailRecoveryService emailRecovery;

    AuthController(IdentityService identity, ProfileService profiles, SessionLogin sessionLogin,
            SessionService sessions, EmailRecoveryService emailRecovery) {
        this.identity = identity;
        this.profiles = profiles;
        this.sessionLogin = sessionLogin;
        this.sessions = sessions;
        this.emailRecovery = emailRecovery;
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    RegisterResponse register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        IdentityService.Registration registration = identity.register(body.username(), body.displayName(),
                body.password(), body.inviteToken(), request.getRemoteAddr());
        sessionLogin.establish(registration.user(), request, response);
        return new RegisterResponse(me(registration.user().id()), registration.recoveryCodes());
    }

    @PostMapping("/auth/login")
    MeResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        PapinhoUser user = identity.authenticate(body.username(), body.password(), body.totpCode(),
                request.getRemoteAddr());
        sessionLogin.establish(user, request, response);
        return me(user.id());
    }

    /** Recupera a conta com um código; encerra todas as sessões e não abre uma nova (o usuário entra de novo). */
    @PostMapping("/auth/recover")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void recover(@Valid @RequestBody RecoverRequest body, HttpServletRequest request) {
        UUID userId = identity.recover(body.username(), body.recoveryCode(), body.newPassword(),
                request.getRemoteAddr());
        sessions.revokeAllExcept(userId, null);
    }

    /** Pede o link de redefinição por e-mail (ID-05); a resposta é a mesma exista ou não a conta. */
    @PostMapping("/auth/password-reset")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void requestPasswordReset(@Valid @RequestBody PasswordResetRequest body, HttpServletRequest request) {
        emailRecovery.requestReset(body.username(), request.getRemoteAddr(), LocaleContextHolder.getLocale());
    }

    /** Redefine a senha pelo link do e-mail e encerra todas as sessões; o usuário entra de novo. */
    @PostMapping("/auth/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest body, HttpServletRequest request) {
        UUID userId = emailRecovery.reset(body.token(), body.newPassword(), request.getRemoteAddr());
        sessions.revokeAllExcept(userId, null);
    }

    /** Confirma o e-mail de recuperação pelo link; não exige sessão (o link pode abrir em outro aparelho). */
    @PostMapping("/auth/email/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void verifyEmail(@Valid @RequestBody TokenRequest body, HttpServletRequest request) {
        emailRecovery.verify(body.token(), request.getRemoteAddr());
    }

    @GetMapping("/me")
    MeResponse current() {
        return me(CurrentUser.id());
    }

    /**
     * Sessão atual para o SPA, pública: devolve {@code me = null} sem sessão em vez de 401, para o primeiro
     * carregamento não gerar erro no console nem precisar tratar exceção.
     */
    @GetMapping("/auth/session")
    SessionResponse session() {
        return new SessionResponse(CurrentUser.get().map(PapinhoUser::id).filter(identity::isActive)
                .map(this::me).orElse(null));
    }

    private MeResponse me(UUID userId) {
        return new MeResponse(identity.account(userId), profiles.own(userId));
    }

    /** Cadastro; {@code inviteToken} é obrigatório no modo somente convite. */
    record RegisterRequest(@NotBlank @Size(max = 64) String username, @NotBlank @Size(max = 64) String displayName,
            @NotBlank @Size(max = 512) String password, @Nullable @Size(max = 128) String inviteToken) {
    }

    record LoginRequest(@NotBlank @Size(max = 64) String username, @NotBlank @Size(max = 512) String password,
            @Nullable @Size(max = 12) String totpCode) {
    }

    record RecoverRequest(@NotBlank @Size(max = 64) String username, @NotBlank @Size(max = 32) String recoveryCode,
            @NotBlank @Size(max = 512) String newPassword) {
    }

    record PasswordResetRequest(@NotBlank @Size(max = 64) String username) {
    }

    record PasswordResetConfirmRequest(@NotBlank @Size(max = 128) String token,
            @NotBlank @Size(max = 512) String newPassword) {
    }

    record TokenRequest(@NotBlank @Size(max = 128) String token) {
    }

    /** Sessão do SPA: {@code me} nulo quando não há login. */
    record SessionResponse(@Nullable MeResponse me) {
    }

    /** Conta e perfil do usuário autenticado. */
    record MeResponse(IdentityService.AccountView account, ProfileView profile) {
    }

    /** Resposta do cadastro: os códigos de recuperação aparecem só aqui. */
    record RegisterResponse(MeResponse me, List<String> recoveryCodes) {
    }
}
