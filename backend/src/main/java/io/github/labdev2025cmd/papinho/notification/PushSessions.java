package io.github.labdev2025cmd.papinho.notification;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpSession;

/**
 * Liga cada assinatura de push à sessão que a criou. Quem só teve a sessão por um tempo (aparelho emprestado,
 * cookie copiado) não continua recebendo as prévias depois que o dono revoga a sessão, troca a senha ou a conta é
 * desativada. A chave é um atributo da sessão, e não o id, porque o id muda a cada login (fixação de sessão).
 */
@Component
class PushSessions {

    static final String ATTRIBUTE = "papinho.push-key";

    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    PushSessions(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    /** Chave da sessão atual, criada na primeira assinatura feita nela. */
    String keyOf(HttpSession session) {
        if (session.getAttribute(ATTRIBUTE) instanceof String key) {
            return key;
        }
        String key = UUID.randomUUID().toString();
        session.setAttribute(ATTRIBUTE, key);
        return key;
    }

    /** Chaves das sessões que a pessoa ainda tem abertas. */
    Set<String> liveKeys(UUID userId) {
        return sessions.findByPrincipalName(userId.toString()).values().stream()
                .map(session -> session.getAttribute(ATTRIBUTE))
                .filter(String.class::isInstance).map(String.class::cast)
                .collect(Collectors.toSet());
    }
}
