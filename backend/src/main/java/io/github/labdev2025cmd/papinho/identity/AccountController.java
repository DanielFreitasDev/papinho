package io.github.labdev2025cmd.papinho.identity;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.labdev2025cmd.papinho.shared.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Configurações da conta: {@code @usuario}, senha, TOTP, códigos de recuperação (ID-03 a ID-05) e exclusão (BK-02). */
@RestController
@RequestMapping("/api/v1/account")
class AccountController {

    private final IdentityService identity;
    private final SessionService sessions;
    private final AccountDeletionService deletion;

    AccountController(IdentityService identity, SessionService sessions, AccountDeletionService deletion) {
        this.identity = identity;
        this.sessions = sessions;
        this.deletion = deletion;
    }

    /** Exclui a própria conta (BK-02) com a senha; encerra todas as sessões, inclusive esta. */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@Valid @RequestBody PasswordRequest body, HttpServletRequest request) {
        deletion.delete(CurrentUser.id(), body.password());
        jakarta.servlet.http.HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @PutMapping("/username")
    IdentityService.AccountView changeUsername(@Valid @RequestBody ChangeUsernameRequest body) {
        return identity.changeUsername(CurrentUser.id(), body.username());
    }

    /** Troca a senha e encerra as outras sessões: quem tinha a senha antiga perde o acesso. */
    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(@Valid @RequestBody ChangePasswordRequest body, HttpServletRequest request) {
        identity.changePassword(CurrentUser.id(), body.currentPassword(), body.newPassword(),
                request.getRemoteAddr());
        sessions.revokeAllExcept(CurrentUser.id(), request.getSession().getId());
    }

    @PostMapping("/totp")
    IdentityService.TotpEnrollment startTotp() {
        return identity.startTotp(CurrentUser.id());
    }

    @PostMapping("/totp/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void confirmTotp(@Valid @RequestBody CodeRequest body) {
        identity.confirmTotp(CurrentUser.id(), body.code());
    }

    @DeleteMapping("/totp")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void disableTotp(@Valid @RequestBody PasswordRequest body) {
        identity.disableTotp(CurrentUser.id(), body.password());
    }

    @PostMapping("/recovery-codes")
    RecoveryCodesResponse regenerateRecoveryCodes(@Valid @RequestBody PasswordRequest body) {
        return new RecoveryCodesResponse(identity.regenerateRecoveryCodes(CurrentUser.id(), body.password()));
    }

    record ChangeUsernameRequest(@NotBlank @Size(max = 64) String username) {
    }

    record ChangePasswordRequest(@NotBlank @Size(max = 512) String currentPassword,
            @NotBlank @Size(max = 512) String newPassword) {
    }

    record CodeRequest(@NotBlank @Size(max = 12) String code) {
    }

    record PasswordRequest(@NotBlank @Size(max = 512) String password) {
    }

    record RecoveryCodesResponse(List<String> recoveryCodes) {
    }
}
