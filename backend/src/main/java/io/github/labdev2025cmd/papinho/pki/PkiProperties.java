package io.github.labdev2025cmd.papinho.pki;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Configuração da PKI da LAN (prefixo {@code app.pki}).
 *
 * @param enabled liga a página {@code /setup} e a porta HTTP auxiliar; desligada em produção (PK-06)
 * @param dir diretório gerado por {@code pki init}, fora do repositório
 * @param httpPort porta HTTP local em que o servidor escuta; só serve {@code /setup/**} e redireciona o resto
 * @param publicHttpPort porta HTTP vista pelos amigos (80 quando há mapeamento de porta ou setcap)
 * @param publicHttpsPort porta HTTPS vista pelos amigos (443 quando há mapeamento de porta ou setcap)
 */
@Validated
@ConfigurationProperties(prefix = "app.pki")
public record PkiProperties(
        @DefaultValue("false") boolean enabled,
        @NotNull Path dir,
        @DefaultValue("8080") @Min(1) @Max(65535) int httpPort,
        @DefaultValue("80") @Min(1) @Max(65535) int publicHttpPort,
        @DefaultValue("443") @Min(1) @Max(65535) int publicHttpsPort) {

    /** {@code host} ou {@code host:porta}, omitindo a porta padrão do esquema. */
    public String httpAuthority(String hostname) {
        return publicHttpPort == 80 ? hostname : hostname + ":" + publicHttpPort;
    }

    /** {@code host} ou {@code host:porta}, omitindo a porta padrão do esquema. */
    public String httpsAuthority(String hostname) {
        return publicHttpsPort == 443 ? hostname : hostname + ":" + publicHttpsPort;
    }
}
