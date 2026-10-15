package io.github.labdev2025cmd.papinho.identity;

import java.util.Map;

import org.springframework.http.HttpStatus;

import io.github.labdev2025cmd.papinho.shared.error.ApiException;

/** Exceções de negócio do módulo de identidade, todas convertidas em Problem Details. */
final class IdentityExceptions {

    private IdentityExceptions() {
    }

    /** Usuário ou senha incorretos, conta desativada ou inexistente: sempre a mesma resposta (ID-04). */
    static class BadCredentialsException extends ApiException {
        BadCredentialsException() {
            super(HttpStatus.UNAUTHORIZED, "bad-credentials", "Authentication failed");
        }
    }

    /** Senha correta, mas a conta exige o código TOTP. */
    static class TotpRequiredException extends ApiException {
        TotpRequiredException() {
            super(HttpStatus.UNAUTHORIZED, "totp-required", Map.of("totpRequired", true), "TOTP code required");
        }
    }

    static class UsernameUnavailableException extends ApiException {
        UsernameUnavailableException() {
            super(HttpStatus.CONFLICT, "username-unavailable", "Username taken, reserved or held");
        }
    }

    static class UsernameCooldownException extends ApiException {
        UsernameCooldownException(long days) {
            super(HttpStatus.CONFLICT, "username-cooldown", Map.of("retryAfterDays", days),
                    "Username changed too recently", days);
        }
    }

    static class WeakPasswordException extends ApiException {
        WeakPasswordException() {
            super(HttpStatus.BAD_REQUEST, "password-weak", "Password does not meet policy");
        }
    }

    static class CompromisedPasswordException extends ApiException {
        CompromisedPasswordException() {
            super(HttpStatus.BAD_REQUEST, "password-compromised", "Password found in breach corpus");
        }
    }

    static class RegistrationClosedException extends ApiException {
        RegistrationClosedException() {
            super(HttpStatus.FORBIDDEN, "registration-closed", "Registration is closed");
        }
    }

    static class InviteInvalidException extends ApiException {
        InviteInvalidException() {
            super(HttpStatus.FORBIDDEN, "invite-invalid", "Invite missing, expired, revoked or exhausted");
        }
    }

    /** Código de recuperação ou TOTP errado. Mesma mensagem para usuário inexistente. */
    static class InvalidCodeException extends ApiException {
        InvalidCodeException() {
            super(HttpStatus.BAD_REQUEST, "code-invalid", "Invalid verification code");
        }
    }

    static class NotFoundException extends ApiException {
        NotFoundException() {
            super(HttpStatus.NOT_FOUND, "not-found", "Resource not found or not owned");
        }
    }
}
