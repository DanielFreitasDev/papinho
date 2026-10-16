package io.github.labdev2025cmd.papinho.shared.error;

import java.net.URI;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import io.github.labdev2025cmd.papinho.shared.security.RateLimitedException;

/**
 * Converte toda exceção da camada web em Problem Details (RFC 9457) com textos vindos dos bundles de mensagem.
 * Exceções inesperadas são registradas uma única vez aqui e respondidas como 500 genérico, sem stack trace.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageSource messageSource;

    GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
        setMessageSource(messageSource);
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApiException(ApiException exception) {
        Locale locale = LocaleContextHolder.getLocale();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.status(), messageSource.getMessage(
                "problem." + exception.slug() + ".detail", exception.detailArguments(), locale));
        problem.setTitle(messageSource.getMessage("problem." + exception.slug() + ".title", null, locale));
        problem.setType(exception.type());
        exception.properties().forEach(problem::setProperty);
        if (exception.status().is5xxServerError()) {
            log.error("Request failed slug={}", exception.slug(), exception);
        }
        ResponseEntity.BodyBuilder response = ResponseEntity.status(exception.status());
        // RFC 6585: o cabeçalho padrão, que clientes como o tus (Uppy) respeitam, além da propriedade no corpo.
        if (exception instanceof RateLimitedException limited) {
            response.header(HttpHeaders.RETRY_AFTER, Long.toString(limited.retryAfterSeconds()));
        }
        return response.body(problem);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        log.error("Unexpected error while handling request", exception);
        Locale locale = LocaleContextHolder.getLocale();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                messageSource.getMessage("problem.internal.detail", null, locale));
        problem.setTitle(messageSource.getMessage("problem.internal.title", null, locale));
        problem.setType(URI.create("urn:papinho:problem:internal"));
        return ResponseEntity.internalServerError().body(problem);
    }

    /** Erros de validação: lista cada campo inválido com a mensagem já traduzida. */
    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Locale locale = LocaleContextHolder.getLocale();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                messageSource.getMessage("problem.validation.detail", null, locale));
        problem.setTitle(messageSource.getMessage("problem.validation.title", null, locale));
        problem.setType(URI.create("urn:papinho:problem:validation"));
        List<FieldViolation> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), messageSource.getMessage(error, locale)))
                .toList();
        problem.setProperty("violations", violations);
        return ResponseEntity.badRequest().body(problem);
    }

    /**
     * Demais erros do próprio Spring MVC (JSON ilegível, id que não é UUID, método ou tipo de mídia não aceitos,
     * corpo grande demais...): troca os textos padrão em inglês pelos dos bundles e dá um tipo próprio.
     */
    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(Exception exception, @Nullable Object body,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body == null && exception instanceof org.springframework.web.ErrorResponse error) {
            body = error.getBody();
        }
        if (body instanceof ProblemDetail problem && !(exception instanceof MethodArgumentNotValidException)) {
            String slug = switch (statusCode.value()) {
                case 400 -> "bad-request";
                case 404 -> "not-found";
                case 405 -> "method-not-allowed";
                case 406 -> "not-acceptable";
                case 413 -> "payload-too-large";
                case 415 -> "unsupported-media-type";
                default -> statusCode.is4xxClientError() ? "request-failed" : "internal";
            };
            Locale locale = LocaleContextHolder.getLocale();
            problem.setTitle(messageSource.getMessage("problem." + slug + ".title", null, locale));
            problem.setDetail(messageSource.getMessage("problem." + slug + ".detail", null, locale));
            problem.setType(URI.create("urn:papinho:problem:" + slug));
        }
        return super.handleExceptionInternal(exception, body, headers, statusCode, request);
    }

    /** Campo inválido devolvido no Problem Details de validação. */
    record FieldViolation(String field, String message) {
    }
}
