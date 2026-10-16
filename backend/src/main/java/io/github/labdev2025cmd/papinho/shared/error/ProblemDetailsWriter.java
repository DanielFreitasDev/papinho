package io.github.labdev2025cmd.papinho.shared.error;

import java.io.IOException;
import java.net.URI;

import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.RequestContextUtils;

import io.github.labdev2025cmd.papinho.shared.security.RateLimitedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Escreve Problem Details direto na resposta, para erros que acontecem em filtros (fora do alcance do
 * {@code @RestControllerAdvice}): 401/403 da segurança e falhas das cerimônias de passkey.
 */
@Component
public class ProblemDetailsWriter {

    private final JsonMapper jsonMapper;
    private final MessageSource messageSource;

    ProblemDetailsWriter(JsonMapper jsonMapper, MessageSource messageSource) {
        this.jsonMapper = jsonMapper;
        this.messageSource = messageSource;
    }

    /**
     * Responde com uma {@link ApiException} levantada num filtro, como o {@code @RestControllerAdvice} faria
     * (argumentos do texto, propriedades e {@code Retry-After} no 429).
     */
    public void write(HttpServletRequest request, HttpServletResponse response, ApiException exception)
            throws IOException {
        var locale = RequestContextUtils.getLocale(request);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.status(), messageSource.getMessage(
                "problem." + exception.slug() + ".detail", exception.detailArguments(), locale));
        problem.setTitle(messageSource.getMessage("problem." + exception.slug() + ".title", null, locale));
        problem.setType(exception.type());
        problem.setInstance(URI.create(request.getRequestURI()));
        exception.properties().forEach(problem::setProperty);
        if (exception instanceof RateLimitedException limited) {
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(limited.retryAfterSeconds()));
        }
        response.setStatus(exception.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(response.getOutputStream(), problem);
    }

    /** Responde com {@code status} e os textos {@code problem.<slug>.*} no idioma da requisição. */
    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String slug)
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
