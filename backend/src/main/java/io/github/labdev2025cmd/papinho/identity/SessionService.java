package io.github.labdev2025cmd.papinho.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

import io.github.labdev2025cmd.papinho.shared.realtime.RealtimeSessions;

/**
 * Sessões ativas do usuário (ID-06), lidas do Spring Session JDBC pelo índice de principal (o id do usuário).
 *
 * <p>O id real da sessão é o próprio segredo do cookie e nunca sai do servidor: a API expõe um
 * identificador derivado (SHA-256 truncado).</p>
 */
@Service
public class SessionService {

    static final String DEVICE_ATTRIBUTE = "papinho.device";
    static final String IP_ATTRIBUTE = "papinho.ip";

    private final FindByIndexNameSessionRepository<? extends Session> sessions;
    private final RealtimeSessions realtime;

    SessionService(FindByIndexNameSessionRepository<? extends Session> sessions, RealtimeSessions realtime) {
        this.sessions = sessions;
        this.realtime = realtime;
    }

    /** Sessões do usuário, a mais recente primeiro, marcando a atual. */
    public List<SessionView> list(UUID userId, @Nullable String currentSessionId) {
        Map<String, ? extends Session> found = sessions.findByPrincipalName(userId.toString());
        return found.values().stream()
                .map(session -> new SessionView(publicId(session.getId()),
                        stringAttribute(session, DEVICE_ATTRIBUTE, "Dispositivo desconhecido"),
                        stringAttribute(session, IP_ATTRIBUTE, "desconhecido"), session.getCreationTime(),
                        session.getLastAccessedTime(), session.getId().equals(currentSessionId)))
                .sorted(Comparator.comparing(SessionView::lastAccessedAt).reversed())
                .toList();
    }

    /** Revoga uma sessão do próprio usuário; id de sessão alheia ou inexistente responde 404 (IDOR). */
    public void revoke(UUID userId, String publicId) {
        String sessionId = sessions.findByPrincipalName(userId.toString()).keySet().stream()
                .filter(id -> publicId(id).equals(publicId)).findFirst()
                .orElseThrow(IdentityExceptions.NotFoundException::new);
        end(sessionId);
    }

    /** Encerra todas as sessões do usuário, menos a informada (que pode ser nula: encerra todas). */
    public int revokeAllExcept(UUID userId, @Nullable String keepSessionId) {
        int count = 0;
        for (String id : sessions.findByPrincipalName(userId.toString()).keySet()) {
            if (!id.equals(keepSessionId)) {
                end(id);
                count++;
            }
        }
        return count;
    }

    /** Apaga a sessão e derruba na hora o WebSocket aberto com ela. */
    private void end(String sessionId) {
        sessions.deleteById(sessionId);
        realtime.closeHttpSession(sessionId);
    }

    static String publicId(String sessionId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(sessionId.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String stringAttribute(Session session, String name, String fallback) {
        Object value = session.getAttribute(name);
        return value instanceof String text ? text : fallback;
    }

    /** Sessão como mostrada ao usuário. */
    public record SessionView(String id, String device, String approximateIp, Instant createdAt,
            Instant lastAccessedAt, boolean current) {
    }
}
