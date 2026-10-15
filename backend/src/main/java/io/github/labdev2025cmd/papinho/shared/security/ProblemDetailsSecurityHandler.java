package io.github.labdev2025cmd.papinho.shared.security;

import java.io.IOException;
import java.net.URI;

import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.RequestContextUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Responde 401 e 403 da camada de segurança como Problem Details (RFC 9457), sem desafio Basic e sem HTML.
 * As mensagens não dizem se um usuário existe nem por que a credencial falhou.
 */
@Component
class ProblemDetailsSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper jsonMapper;
    private final MessageSource messageSource;

    ProblemDetailsSecurityHandler(JsonMapper jsonMapper, MessageSource messageSource) {
        this.jsonMapper = jsonMapper;
        this.messageSource = messageSource;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        write(request, response, HttpStatus.UNAUTHORIZED, "unauthorized");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        write(request, response, HttpStatus.FORBIDDEN, "forbidden");
    }

    private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String slug)
            throws IOException {
        var locale = RequestContextUtils.getLocale(request);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status,
                messageSource.getMessage("problem." + slug + ".detail", null, locale));
        problem.setTitle(messageSource.getMessage("problem." + slug + ".title", null, locale));
        problem.setType(URI.create("urn:papinho:problem:" + slug));
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(response.getOutputStream(), problem);
    }
}
