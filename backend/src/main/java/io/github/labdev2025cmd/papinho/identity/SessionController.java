package io.github.labdev2025cmd.papinho.identity;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.labdev2025cmd.papinho.shared.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/** Tela de sessões ativas (ID-06). */
@RestController
@RequestMapping("/api/v1/sessions")
class SessionController {

    private final SessionService sessions;

    SessionController(SessionService sessions) {
        this.sessions = sessions;
    }

    @GetMapping
    List<SessionService.SessionView> list(HttpServletRequest request) {
        HttpSession current = request.getSession(false);
        return sessions.list(CurrentUser.id(), current == null ? null : current.getId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@PathVariable String id) {
        sessions.revoke(CurrentUser.id(), id);
    }

    /** Encerra todas as outras sessões, mantendo a atual. */
    @DeleteMapping
    RevokedResponse revokeOthers(HttpServletRequest request) {
        return new RevokedResponse(sessions.revokeAllExcept(CurrentUser.id(), request.getSession().getId()));
    }

    record RevokedResponse(int revoked) {
    }
}
