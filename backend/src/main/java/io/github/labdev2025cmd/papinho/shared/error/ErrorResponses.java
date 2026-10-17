package io.github.labdev2025cmd.papinho.shared.error;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Respostas de erro que nascem antes do Spring MVC (StrictHttpFirewall, página de erro do Tomcat): Problem Details
 * escrito à mão e os cabeçalhos de segurança que o filtro do Spring Security não chegou a pôr (SG-01, SG-02).
 */
public final class ErrorResponses {

    /** Erro não carrega conteúdo ativo: a política mais fechada possível. */
    static final String CONTENT_SECURITY_POLICY = "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; "
            + "form-action 'none'";

    private ErrorResponses() {
    }

    /** Slug do Problem Details para um status sem exceção própria. */
    public static String slug(int status) {
        return switch (status) {
            case 400 -> "bad-request";
            case 401 -> "unauthorized";
            case 403 -> "forbidden";
            case 404 -> "not-found";
            case 405 -> "method-not-allowed";
            case 415 -> "unsupported-media-type";
            case 429 -> "rate-limited";
            default -> status >= 500 ? "internal" : "bad-request";
        };
    }

    public static void securityHeaders(HttpServletResponse response) {
        response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "same-origin");
        response.setHeader("Cross-Origin-Resource-Policy", "same-origin");
        response.setHeader("Cache-Control", "no-store");
    }

    /**
     * Escreve o Problem Details com os textos do bundle padrão (pt-BR), sem depender do contexto do Spring: a
     * válvula do Tomcat roda fora dele.
     */
    static void write(HttpServletResponse response, int status, String instance) throws IOException {
        String slug = slug(status);
        ResourceBundle bundle = ResourceBundle.getBundle("messages", Locale.ROOT);
        String json = "{\"type\":\"urn:papinho:problem:" + slug + "\",\"title\":" + quote(text(bundle, slug, "title"))
                + ",\"status\":" + status + ",\"detail\":" + quote(text(bundle, slug, "detail")) + ",\"instance\":"
                + quote(instance) + "}";
        securityHeaders(response);
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        response.setContentLength(body.length);
        try (OutputStream out = response.getOutputStream()) {
            out.write(body);
        }
    }

    private static String text(ResourceBundle bundle, String slug, String part) {
        try {
            return bundle.getString("problem." + slug + "." + part);
        } catch (MissingResourceException missing) {
            return bundle.getString("problem.internal." + part);
        }
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
