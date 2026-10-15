package io.github.labdev2025cmd.papinho.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.sun.net.httpserver.HttpServer;

import io.github.labdev2025cmd.papinho.support.Accounts;
import io.github.labdev2025cmd.papinho.support.ApiClient;
import io.github.labdev2025cmd.papinho.support.IntegrationTest;
import io.github.labdev2025cmd.papinho.support.TestSupportConfiguration.ApiClients;
import tools.jackson.databind.JsonNode;

/** GIFs por provedor (MS-10), contra um Tenor simulado: proxy da busca, prévia e GIF guardado como mídia. */
@IntegrationTest
class GifIT {

    /** GIF de 1×1 pixel. */
    private static final byte[] GIF = Base64.getDecoder()
            .decode("R0lGODlhAQABAIAAAP///wAAACH5BAEAAAAALAAAAAABAAEAAAICRAEAOw==");
    private static final HttpServer TENOR;
    private static final AtomicReference<String> LAST_QUERY = new AtomicReference<>();

    static {
        try {
            TENOR = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        TENOR.createContext("/v2/search", exchange -> {
            LAST_QUERY.set(exchange.getRequestURI().getRawQuery());
            String base = "http://127.0.0.1:" + TENOR.getAddress().getPort();
            byte[] body = ("{\"results\":[{\"id\":\"42\",\"content_description\":\"gato dançando\","
                    + "\"media_formats\":{\"gif\":{\"url\":\"" + base + "/media/gato.gif\",\"dims\":[1,1]},"
                    + "\"tinygif\":{\"url\":\"" + base + "/media/gato-tiny.gif\",\"dims\":[1,1]}}},"
                    + "{\"id\":\"43\",\"content_description\":\"fora\",\"media_formats\":{"
                    + "\"gif\":{\"url\":\"http://169.254.169.254/latest\",\"dims\":[1,1]},"
                    + "\"tinygif\":{\"url\":\"http://169.254.169.254/latest\",\"dims\":[1,1]}}}]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        TENOR.createContext("/media/", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "image/gif");
            exchange.sendResponseHeaders(200, GIF.length);
            exchange.getResponseBody().write(GIF);
            exchange.close();
        });
        TENOR.start();
    }

    @DynamicPropertySource
    static void tenor(DynamicPropertyRegistry registry) {
        registry.add("app.gifs.provider", () -> "tenor");
        registry.add("app.gifs.api-key", () -> "chave-secreta");
        registry.add("app.gifs.base-url", () -> "http://127.0.0.1:" + TENOR.getAddress().getPort());
        registry.add("app.gifs.media-hosts", () -> "127.0.0.1");
    }

    @AfterAll
    static void stop() {
        TENOR.stop(0);
    }

    @Autowired
    private ApiClients clients;

    private ApiClient ana;

    @BeforeEach
    void setUp() {
        ana = clients.create();
        Accounts.register(ana, "ana", null);
    }

    @Test
    @DisplayName("busca pelo servidor sem expor URL nem chave; prévia e anexo só de hosts permitidos")
    void shouldProxySearchAndAttach() {
        assertThat(ana.json(ana.get("/api/v1/gifs/status")).get("enabled").asBoolean()).isTrue();
        JsonNode results = ana.json(ana.get("/api/v1/gifs?q=gato"));
        assertThat(LAST_QUERY.get()).contains("q=gato").contains("key=chave-secreta");
        assertThat(results).hasSize(2);
        assertThat(results.toString()).doesNotContain("127.0.0.1").doesNotContain("chave");
        String key = results.get(0).get("id").asString();
        assertThat(results.get(0).get("title").asString()).isEqualTo("gato dançando");

        var preview = ana.get("/api/v1/gifs/" + key + "/preview");
        assertThat(preview).hasStatusOk();
        assertThat(preview.getResponse().getContentAsByteArray()).isEqualTo(GIF);
        assertThat(ana.get("/api/v1/gifs/" + results.get(1).get("id").asString() + "/preview"))
                .as("host fora da lista").hasStatus(502);
        assertThat(ana.get("/api/v1/gifs/" + UUID.randomUUID() + "/preview")).hasStatus(404);

        JsonNode media = ana.json(ana.post("/api/v1/gifs/" + key + "/attach", null));
        assertThat(media.get("contentType").asString()).isEqualTo("image/gif");
        assertThat(media.get("kind").asString()).isEqualTo("IMAGE");
        assertThat(ana.get("/api/v1/media/" + media.get("id").asString() + "/content")).hasStatusOk();
        assertThat(Map.of()).isEmpty();
    }
}
