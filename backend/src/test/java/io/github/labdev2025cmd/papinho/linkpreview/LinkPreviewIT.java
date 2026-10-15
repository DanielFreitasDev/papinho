package io.github.labdev2025cmd.papinho.linkpreview;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

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

/**
 * Prévia de link de ponta a ponta (MD-04, MD-05) contra um site local; só a porta dele é liberada no loopback
 * ({@code app.link-preview.test-allowed-ports}).
 */
@IntegrationTest
class LinkPreviewIT {

    private static final HttpServer SITE;
    private static final AtomicInteger PAGE_HITS = new AtomicInteger();

    static {
        try {
            SITE = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        SITE.createContext("/artigo", exchange -> {
            PAGE_HITS.incrementAndGet();
            byte[] body = ("<html><head><title>fallback</title>"
                    + "<meta property=\"og:title\" content=\"Receita de cuscuz\">"
                    + "<meta property=\"og:description\" content=\"Com manteiga da terra\">"
                    + "<meta property=\"og:site_name\" content=\"Cozinha\">"
                    + "<meta property=\"og:image\" content=\"/capa.png\">"
                    + "<script>alert(1)</script></head><body>oi</body></html>").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        SITE.createContext("/capa.png", exchange -> {
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(new java.awt.image.BufferedImage(800, 600, java.awt.image.BufferedImage.TYPE_INT_RGB),
                    "png", png);
            exchange.getResponseHeaders().add("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, png.size());
            exchange.getResponseBody().write(png.toByteArray());
            exchange.close();
        });
        SITE.start();
    }

    @DynamicPropertySource
    static void sitePort(DynamicPropertyRegistry registry) {
        registry.add("app.link-preview.test-allowed-ports", () -> SITE.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        SITE.stop(0);
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
    @DisplayName("Open Graph com imagem recodificada pelo servidor, em cache para a próxima pessoa")
    void shouldBuildAndCachePreview() throws IOException {
        String url = "http://127.0.0.1:" + SITE.getAddress().getPort() + "/artigo";
        JsonNode preview = ana.json(ana.get(previewPath(url)));
        assertThat(preview.get("available").asBoolean()).isTrue();
        assertThat(preview.get("title").asString()).isEqualTo("Receita de cuscuz");
        assertThat(preview.get("description").asString()).isEqualTo("Com manteiga da terra");
        assertThat(preview.get("siteName").asString()).isEqualTo("Cozinha");
        String image = preview.get("imageUrl").asString();
        var jpeg = ana.get(image);
        assertThat(jpeg).hasStatusOk();
        assertThat(jpeg.getResponse().getContentType()).isEqualTo("image/jpeg");
        var decoded = ImageIO.read(new java.io.ByteArrayInputStream(jpeg.getResponse().getContentAsByteArray()));
        assertThat(decoded.getWidth()).isEqualTo(LinkPreviewService.IMAGE_SIDE);

        ApiClient bruno = clients.create();
        Accounts.register(bruno, "bruno", Accounts.invite(ana));
        assertThat(bruno.json(bruno.get(previewPath(url))).get("title").asString()).isEqualTo("Receita de cuscuz");
        assertThat(PAGE_HITS.get()).as("segunda pessoa usa o cache").isEqualTo(1);
    }

    @Test
    @DisplayName("rede local e metadados de nuvem não são buscados; outros esquemas são recusados")
    void shouldRefusePrivateTargets() {
        for (String url : new String[] {"http://10.0.0.1/", "http://169.254.169.254/latest/meta-data/",
            "http://127.0.0.1/admin", "http://192.168.0.1/"}) {
            JsonNode preview = ana.json(ana.get(previewPath(url)));
            assertThat(preview.get("available").asBoolean()).as(url).isFalse();
        }
        assertThat(ana.get(previewPath("file:///etc/passwd"))).hasStatus(400);
        assertThat(ana.get(previewPath("javascript:alert(1)"))).hasStatus(400);
    }

    @Test
    @DisplayName("YouTube vira youtube-nocookie para carregar só no clique")
    void shouldEmbedYoutubeWithoutCookies() {
        JsonNode preview = ana.json(ana.get(previewPath("https://youtu.be/dQw4w9WgXcQ")));
        assertThat(preview.get("embedProvider").asString()).isEqualTo("YOUTUBE");
        assertThat(preview.get("embedSrc").asString())
                .isEqualTo("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ");
        assertThat(preview.get("available").asBoolean()).isTrue();
    }

    private static String previewPath(String url) {
        // O MockMvc codifica o template da URL; codificar aqui daria codificação dupla.
        return "/api/v1/link-previews?url=" + url;
    }
}
