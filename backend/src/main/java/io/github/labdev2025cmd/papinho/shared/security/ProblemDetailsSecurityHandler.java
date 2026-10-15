package io.github.labdev2025cmd.papinho.shared.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import io.github.labdev2025cmd.papinho.shared.error.ProblemDetailsWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Responde 401 e 403 da camada de segurança como Problem Details (RFC 9457), sem desafio Basic e sem HTML.
 * As mensagens não dizem se um usuário existe nem por que a credencial falhou.
 */
@Component
class ProblemDetailsSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ProblemDetailsWriter writer;

    ProblemDetailsSecurityHandler(ProblemDetailsWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        writer.write(request, response, HttpStatus.UNAUTHORIZED, "unauthorized");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        writer.write(request, response, HttpStatus.FORBIDDEN, "forbidden");
    }
}
