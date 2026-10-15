package io.github.labdev2025cmd.papinho.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Cabeçalhos de segurança HTTP (prefixo {@code app.security}).
 *
 * @param hsts liga o {@code Strict-Transport-Security}; desligado na LAN (PKI própria) e ligado em produção
 * @param contentSecurityPolicy política CSP enviada em todas as respostas; nada de {@code unsafe-inline}
 * @param permissionsPolicy política de permissões do navegador (microfone só na própria origem, para áudio)
 * @param rateLimitMultiplier multiplica a capacidade de todos os limites de tentativas (SG-03); 1 em produção. Só
 *        os testes e2e, que rodam tudo de um IP só, sobem este valor
 */
@Validated
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        @DefaultValue("false") boolean hsts,
        @DefaultValue(DEFAULT_CSP) @NotBlank String contentSecurityPolicy,
        @DefaultValue(DEFAULT_PERMISSIONS_POLICY) @NotBlank String permissionsPolicy,
        @DefaultValue("1") @Min(1) @Max(100) int rateLimitMultiplier) {

    /** CSP estrita: só a própria origem; iframes apenas dos provedores permitidos de incorporação (MD-05). */
    public static final String DEFAULT_CSP = "default-src 'self'; "
            + "script-src 'self'; "
            + "style-src 'self'; "
            + "img-src 'self' data: blob:; "
            + "media-src 'self' blob:; "
            + "font-src 'self'; "
            + "connect-src 'self'; "
            + "worker-src 'self' blob:; "
            + "manifest-src 'self'; "
            + "frame-src https://www.youtube-nocookie.com https://player.vimeo.com https://open.spotify.com; "
            + "object-src 'none'; "
            + "base-uri 'self'; "
            + "form-action 'self'; "
            + "frame-ancestors 'none'";

    public static final String DEFAULT_PERMISSIONS_POLICY =
            "camera=(self), microphone=(self), geolocation=(), payment=(), usb=(), interest-cohort=()";
}
