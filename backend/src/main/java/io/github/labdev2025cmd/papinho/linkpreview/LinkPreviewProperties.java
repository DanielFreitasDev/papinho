package io.github.labdev2025cmd.papinho.linkpreview;

import java.time.Duration;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Prévia de links (prefixo {@code app.link-preview}).
 *
 * @param enabled liga a busca de prévias (desligada, o cliente mostra só o link)
 * @param cacheTtl validade de uma prévia em cache
 * @param testAllowedPorts só em testes: portas locais liberadas além de 80 e 443, com loopback permitido
 */
@ConfigurationProperties(prefix = "app.link-preview")
public record LinkPreviewProperties(@DefaultValue("true") boolean enabled, @DefaultValue("24h") Duration cacheTtl,
        @DefaultValue Set<Integer> testAllowedPorts) {
}
