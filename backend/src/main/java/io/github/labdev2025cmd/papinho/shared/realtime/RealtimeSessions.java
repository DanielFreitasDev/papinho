package io.github.labdev2025cmd.papinho.shared.realtime;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

/**
 * WebSockets abertos e a sessão HTTP de cada um. Logout, sessão revogada (ID-06) ou conta desativada apagam a
 * sessão HTTP; a varredura periódica fecha o WebSocket correspondente, que não pode sobreviver a ela.
 */
@Component
public class RealtimeSessions {

    /** Código de fechamento para "sessão encerrada": o cliente não deve reconectar sem novo login. */
    public static final CloseStatus SESSION_ENDED = new CloseStatus(4401, "session ended");

    private static final Logger log = LoggerFactory.getLogger(RealtimeSessions.class);

    private final Map<String, WebSocketSession> open = new ConcurrentHashMap<>();
    private final SessionRepository<? extends Session> httpSessions;

    RealtimeSessions(SessionRepository<? extends Session> httpSessions) {
        this.httpSessions = httpSessions;
    }

    WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                open.put(session.getId(), session);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                open.remove(session.getId());
                super.afterConnectionClosed(session, status);
            }
        };
    }

    /** Fecha os WebSockets cuja sessão HTTP não existe mais. */
    @Scheduled(fixedDelayString = "${app.realtime.session-check:30s}")
    public void closeOrphans() {
        for (WebSocketSession session : open.values()) {
            Object httpSessionId = session.getAttributes().get(HttpSessionHandshakeInterceptor.HTTP_SESSION_ID_ATTR_NAME);
            if (!(httpSessionId instanceof String id) || httpSessions.findById(id) == null) {
                close(session);
            }
        }
    }

    int openCount() {
        return open.size();
    }

    private void close(WebSocketSession session) {
        open.remove(session.getId());
        try {
            session.close(SESSION_ENDED);
        } catch (IOException exception) {
            log.debug("WebSocket already closed id={}", session.getId());
        }
    }
}
