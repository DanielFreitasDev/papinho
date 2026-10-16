package io.github.labdev2025cmd.papinho.identity;

import java.net.URI;
import java.time.Duration;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * E-mail opcional de recuperação (prefixo {@code app.mail}, ID-05). Desligado por padrão; o servidor SMTP vem de
 * {@code spring.mail.*}.
 *
 * @param enabled liga o e-mail de recuperação
 * @param from remetente das mensagens
 * @param publicUrl endereço público da instância usado nos links; nunca vem do cabeçalho {@code Host} da
 *                  requisição, que um atacante controla
 * @param verifyValidity validade do link de confirmação do endereço
 * @param resetValidity validade do link de redefinição de senha
 */
@ConfigurationProperties(prefix = "app.mail")
record MailProperties(@DefaultValue("false") boolean enabled, @Nullable String from, @Nullable String publicUrl,
        @DefaultValue("24h") Duration verifyValidity, @DefaultValue("1h") Duration resetValidity) {

    MailProperties {
        if (enabled && (from == null || from.isBlank() || publicUrl == null || !isHttpUrl(publicUrl))) {
            throw new IllegalStateException(
                    "app.mail.enabled exige PAPINHO_MAIL_FROM e PAPINHO_PUBLIC_URL (e o servidor em spring.mail.*)");
        }
    }

    private static boolean isHttpUrl(String value) {
        try {
            URI uri = URI.create(value.strip());
            return ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) && uri.getHost() != null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /** Link do SPA com o token no fragmento: o fragmento não vai ao servidor nem aparece em logs de acesso. */
    String link(String path, String token) {
        if (publicUrl == null) {
            throw new IllegalStateException("app.mail.public-url not configured");
        }
        String base = publicUrl.strip().replaceAll("/+$", "");
        return base + path + "#token=" + token;
    }
}
