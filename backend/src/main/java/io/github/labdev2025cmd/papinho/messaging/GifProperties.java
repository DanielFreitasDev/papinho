package io.github.labdev2025cmd.papinho.messaging;

import java.net.URI;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * GIFs por provedor externo (prefixo {@code app.gifs}, MS-10). Desligado por padrão: nada sai do servidor.
 *
 * @param provider {@code none} (padrão) ou {@code tenor}
 * @param apiKey chave da API do provedor; nunca vai para o cliente nem para os logs
 * @param baseUrl endereço da API (Tenor v2)
 * @param mediaHosts únicos hosts de onde o servidor baixa os GIFs escolhidos (contra SSRF)
 * @param maxBytes limite de cada GIF baixado
 */
@ConfigurationProperties(prefix = "app.gifs")
public record GifProperties(@DefaultValue("none") String provider, @Nullable String apiKey,
        @DefaultValue("https://tenor.googleapis.com") URI baseUrl, @DefaultValue("media.tenor.com") Set<String> mediaHosts,
        @DefaultValue("8388608") long maxBytes) {

    boolean enabled() {
        return "tenor".equals(provider) && apiKey != null && !apiKey.isBlank();
    }
}
