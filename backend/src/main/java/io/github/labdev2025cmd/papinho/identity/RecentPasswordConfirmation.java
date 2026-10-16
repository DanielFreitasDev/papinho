package io.github.labdev2025cmd.papinho.identity;

import java.time.Clock;
import java.time.Duration;

import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Senha confirmada há pouco (modo "sudo"): mudar como se entra na conta (passkey nova, ligar o TOTP) exige a senha
 * digitada nos últimos minutos, no login por senha ou no diálogo de confirmação. Quem só tem a sessão (aparelho
 * destravado, cookie copiado) não troca as credenciais da pessoa.
 */
@Component
class RecentPasswordConfirmation {

    /** Atributo da sessão com o instante (segundos da época) da última senha confirmada. */
    static final String ATTRIBUTE = "papinho.password-confirmed-at";
    static final Duration VALIDITY = Duration.ofMinutes(5);

    private final Clock clock;

    RecentPasswordConfirmation(Clock clock) {
        this.clock = clock;
    }

    void mark(HttpServletRequest request) {
        request.getSession().setAttribute(ATTRIBUTE, clock.instant().getEpochSecond());
    }

    boolean isRecent(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && session.getAttribute(ATTRIBUTE) instanceof Long confirmedAt
                && clock.instant().getEpochSecond() - confirmedAt <= VALIDITY.toSeconds();
    }

    void require(HttpServletRequest request) {
        if (!isRecent(request)) {
            throw new IdentityExceptions.PasswordConfirmationRequiredException();
        }
    }
}
