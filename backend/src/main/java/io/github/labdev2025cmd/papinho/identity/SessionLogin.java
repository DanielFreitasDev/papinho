package io.github.labdev2025cmd.papinho.identity;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;

import io.github.labdev2025cmd.papinho.shared.security.PapinhoUser;
import io.github.labdev2025cmd.papinho.shared.web.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Abre a sessão autenticada depois do login por senha ou do cadastro (ADR 0003).
 *
 * <p>Troca o id da sessão (proteção contra fixação), gera um token CSRF novo e grava o contexto de segurança
 * na sessão JDBC, junto com a descrição do dispositivo para a tela de sessões. A senha acabou de ser digitada:
 * vale como confirmação recente para o modo sudo ({@link RecentPasswordConfirmation}).</p>
 */
@Component
class SessionLogin {

    private final SecurityContextRepository contextRepository;
    private final CsrfAuthenticationStrategy csrfStrategy;
    private final RecentPasswordConfirmation recentConfirmation;
    private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();

    SessionLogin(SecurityContextRepository contextRepository, CsrfTokenRepository csrfTokenRepository,
            RecentPasswordConfirmation recentConfirmation) {
        this.contextRepository = contextRepository;
        this.csrfStrategy = new CsrfAuthenticationStrategy(csrfTokenRepository);
        this.recentConfirmation = recentConfirmation;
    }

    void establish(PapinhoUser user, HttpServletRequest request, HttpServletResponse response) {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            request.changeSessionId();
        } else {
            request.getSession(true);
        }
        csrfStrategy.onAuthentication(authentication, request, response);
        // O token novo é adiado; carregá-lo agora grava o cookie XSRF-TOKEN nesta mesma resposta.
        if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) {
            token.getToken();
        }
        SecurityContext context = holder.createEmptyContext();
        context.setAuthentication(authentication);
        holder.setContext(context);
        contextRepository.saveContext(context, request, response);
        // Dados mostrados na tela de sessões (ID-06); o IP completo nunca é guardado.
        ClientInfo client = ClientInfo.from(request);
        HttpSession session = request.getSession();
        session.setAttribute(SessionService.DEVICE_ATTRIBUTE, client.device());
        session.setAttribute(SessionService.IP_ATTRIBUTE, client.approximateIp());
        recentConfirmation.mark(request);
    }
}
