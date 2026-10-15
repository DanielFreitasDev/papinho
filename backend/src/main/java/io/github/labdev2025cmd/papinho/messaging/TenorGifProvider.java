package io.github.labdev2025cmd.papinho.messaging;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tenor API v2 (MS-10), usada só quando {@code app.gifs.provider=tenor} e há chave. O servidor faz o papel de
 * proxy: o navegador nunca fala com o provedor.
 */
@Component
class TenorGifProvider implements GifProvider {

    private static final Logger log = LoggerFactory.getLogger(TenorGifProvider.class);

    private final GifProperties properties;
    private final JsonMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    TenorGifProvider(GifProperties properties, JsonMapper json) {
        this.properties = properties;
        this.json = json;
    }

    @Override
    public boolean enabled() {
        return properties.enabled();
    }

    @Override
    public List<Gif> search(String query, String locale, int limit) {
        String path = query.isBlank() ? "/v2/featured" : "/v2/search";
        String uri = properties.baseUrl() + path + "?key=" + encode(properties.apiKey()) + "&limit=" + limit
                + "&media_filter=gif,tinygif&contentfilter=medium&locale=" + encode(locale)
                + (query.isBlank() ? "" : "&q=" + encode(query));
        try {
            HttpResponse<byte[]> response = http.send(HttpRequest.newBuilder(URI.create(uri))
                    .timeout(Duration.ofSeconds(8)).header("User-Agent", "Papinho").GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                log.warn("Provedor de GIF respondeu {}", response.statusCode());
                throw new MessagingExceptions.GifUnavailableException();
            }
            List<Gif> result = new ArrayList<>();
            for (JsonNode item : json.readTree(response.body()).path("results")) {
                JsonNode gif = item.path("media_formats").path("gif");
                JsonNode tiny = item.path("media_formats").path("tinygif");
                if (gif.path("url").isMissingNode() || tiny.path("url").isMissingNode()) {
                    continue;
                }
                result.add(new Gif(item.path("id").asString(), item.path("content_description").asString(""),
                        URI.create(tiny.path("url").asString()), URI.create(gif.path("url").asString()),
                        gif.path("dims").path(0).asInt(0), gif.path("dims").path(1).asInt(0)));
            }
            return result;
        } catch (IOException e) {
            // Sem a URL: ela tem a chave da API.
            log.warn("Falha ao consultar o provedor de GIF: {}", e.getClass().getSimpleName());
            throw new MessagingExceptions.GifUnavailableException();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MessagingExceptions.GifUnavailableException();
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
