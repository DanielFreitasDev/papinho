package io.github.labdev2025cmd.papinho.shared.security;

import java.io.IOException;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Materializa o token CSRF adiado para que o cookie {@code XSRF-TOKEN} chegue ao SPA já na primeira resposta.
 *
 * <p>{@code csrf.spa()} só grava o cookie quando alguém lê o token; sem este filtro o primeiro POST do SPA
 * falharia por falta do cookie.</p>
 */
final class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) {
            token.getToken();
        }
        chain.doFilter(request, response);
    }
}
