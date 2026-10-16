package io.github.labdev2025cmd.papinho.privacy;

import org.springframework.http.HttpStatus;

import io.github.labdev2025cmd.papinho.shared.error.ApiException;

/** Erros do módulo de privacidade. */
final class PrivacyExceptions {

    private PrivacyExceptions() {
    }

    /** Valor fora do permitido (atraso de revelação, atalho, minutos de inatividade, código). */
    static class InvalidPrivacyException extends ApiException {
        InvalidPrivacyException(String reason) {
            super(HttpStatus.BAD_REQUEST, "privacy-invalid", "Invalid privacy setting: " + reason);
        }
    }

    /** Segredo do bloqueio de tela errado; a mesma resposta para tudo (sem dizer o que falhou). */
    static class WrongScreenSecretException extends ApiException {
        WrongScreenSecretException() {
            super(HttpStatus.FORBIDDEN, "screen-lock-invalid", "Wrong screen lock secret");
        }
    }

    /** Bloqueio de tela não configurado. */
    static class ScreenLockMissingException extends ApiException {
        ScreenLockMissingException() {
            super(HttpStatus.NOT_FOUND, "screen-lock-missing", "Screen lock not configured");
        }
    }

    /** Código de conversa trancada errado. */
    static class WrongLockCodeException extends ApiException {
        WrongLockCodeException() {
            super(HttpStatus.FORBIDDEN, "lock-code-invalid", "Wrong lock code");
        }
    }

    /** Conversa desconhecida ou de que a pessoa não participa. */
    static class ConversationNotFoundException extends ApiException {
        ConversationNotFoundException() {
            super(HttpStatus.NOT_FOUND, "conversation-not-found", "Conversation not found");
        }
    }
}
