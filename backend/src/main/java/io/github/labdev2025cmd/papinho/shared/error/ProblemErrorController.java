package io.github.labdev2025cmd.papinho.shared.error;

import java.net.URI;
import java.util.Locale;

import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Substitui o {@code /error} do Spring Boot (JSON com timestamp ou página HTML): erros despachados pelo
 * container (método que o Tomcat recusa, {@code sendError} de um filtro) também saem em Problem Details (SG-01).
 */
@RestController
class ProblemErrorController implements ErrorController {

    private final MessageSource messageSource;

    ProblemErrorController(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @RequestMapping("${server.error.path:/error}")
    ResponseEntity<ProblemDetail> error(HttpServletRequest request, HttpServletResponse response) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatus status = HttpStatus.resolve(code instanceof Integer value ? value : 500);
        if (status == null || !status.isError()) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String slug = ErrorResponses.slug(status.value());
        Locale locale = LocaleContextHolder.getLocale();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status,
                messageSource.getMessage("problem." + slug + ".detail", null, locale));
        problem.setTitle(messageSource.getMessage("problem." + slug + ".title", null, locale));
        problem.setType(URI.create("urn:papinho:problem:" + slug));
        if (request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI) instanceof String uri) {
            problem.setInstance(URI.create(uri.replaceAll("[^A-Za-z0-9/._~-]", "_")));
        }
        ErrorResponses.securityHeaders(response);
        return ResponseEntity.status(status).contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
