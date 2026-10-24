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
    /** Quantas pessoas pedem o mesmo link ao mesmo tempo no teste da corrida (o máximo por site no SafeFetcher). */
    private static final int AT_ONCE = 4;
    private static final java.util.concurrent.CountDownLatch ARRIVALS = new java.util.concurrent.CountDownLatch(
            AT_ONCE);

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
        // oEmbed simulados (MD-05): o Vimeo "666" responde com um iframe de outro host, que deve ser ignorado.
        SITE.createContext("/vimeo-oembed", exchange -> {
            String query = java.net.URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
            String src = query.contains("/666") ? "https://evil.example/video/666"
                    : "https://player.vimeo.com/video/76979871?h=8272103f6e";
            oembed(exchange, "{\"type\":\"video\",\"title\":\"Curta no Vimeo\",\"html\":\"<iframe src=\\\""
                    + src + "\\\" width=\\\"640\\\"></iframe><script>alert(1)</script>\"}");
        });
        SITE.createContext("/spotify-oembed", exchange -> oembed(exchange,
                "{\"title\":\"Asa Branca\",\"html\":\"<iframe style=\\\"border-radius: 12px\\\" "
                        + "src=\\\"https://open.spotify.com/embed/track/4uLU6hMCjMI75M1A2tKUQC?utm_source=oembed\\\">"
                        + "</iframe>\"}"));
        // Responde só quando todas as buscas do mesmo link chegaram: ninguém achou a prévia no cache.
        SITE.createContext("/juntos", exchange -> {
            ARRIVALS.countDown();
            try {
                ARRIVALS.await(2, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            byte[] body = "<html><head><title>Ata da assembleia</title></head></html>"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        // Site que goteja: 1 byte a cada 500 ms por 40 s, como quem quer prender a busca (MD-04).
        SITE.createContext("/goteja", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, 0);
            try (var out = exchange.getResponseBody()) {
                for (int i = 0; i < 80; i++) {
                    out.write('<');
                    out.flush();
                    Thread.sleep(500);
                }
            } catch (IOException | InterruptedException aborted) {
                // O Papinho desistiu da busca.
            }
        });
        SITE.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        SITE.start();
    }

    private static void oembed(com.sun.net.httpserver.HttpExchange exchange, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    @DynamicPropertySource
    static void sitePort(DynamicPropertyRegistry registry) {
        registry.add("app.link-preview.test-allowed-ports", () -> SITE.getAddress().getPort());
        registry.add("app.link-preview.fetch-timeout", () -> "3s");
        registry.add("app.link-preview.vimeo-oembed",
                () -> "http://127.0.0.1:" + SITE.getAddress().getPort() + "/vimeo-oembed");
        registry.add("app.link-preview.spotify-oembed",
                () -> "http://127.0.0.1:" + SITE.getAddress().getPort() + "/spotify-oembed");
    }

    @AfterAll
    static void stop() {
        SITE.stop(0);
    }

    @Autowired
    private ApiClients clients;

    @Autowired
    private javax.sql.DataSource dataSource;

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
    @DisplayName("o mesmo link pedido por várias pessoas ao mesmo tempo, fora do cache, não dá erro")
    void shouldServeTheSameLinkFetchedAtOnce() throws Exception {
        // Quem envia e quem recebe abrem a prévia juntos: todos buscam e gravam a mesma linha do cache.
        String url = "http://127.0.0.1:" + SITE.getAddress().getPort() + "/juntos";
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var results = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for (int i = 0; i < AT_ONCE; i++) {
                results.add(executor.submit(() -> ana.get(previewPath(url)).getResponse().getStatus()));
            }
            for (var result : results) {
                assertThat(result.get(30, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(200);
            }
        }
        assertThat(ARRIVALS.getCount()).as("buscas simultâneas que não acharam o cache").isZero();
        assertThat(ana.json(ana.get(previewPath(url))).get("title").asString()).isEqualTo("Ata da assembleia");
    }

    @Test
    @DisplayName("rede local e metadados de nuvem não são buscados; outros esquemas são recusados")
    void shouldRefusePrivateTargets() {
        // Sem loopback: o IT libera o 127.0.0.1 para o site de teste, e um servidor na porta 80 da máquina
        // passaria. A recusa do loopback com a regra de produção está no SafeFetcherTest.
        for (String url : new String[] {"http://10.0.0.1/", "http://169.254.169.254/latest/meta-data/",
            "http://192.168.0.1/", "http://[fd00::1]/"}) {
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

    @Test
    @DisplayName("Vimeo e Spotify pelo oEmbed: só o src do iframe em host permitido, nunca o HTML do provedor")
    void shouldEmbedVimeoAndSpotifyThroughOembed() {
        JsonNode vimeo = ana.json(ana.get(previewPath("https://vimeo.com/76979871")));
        assertThat(vimeo.get("embedProvider").asString()).isEqualTo("VIMEO");
        assertThat(vimeo.get("embedSrc").asString()).isEqualTo("https://player.vimeo.com/video/76979871?h=8272103f6e");
        // O título pode vir da própria página quando há internet (no CI); sem rede, vem do oEmbed.
        assertThat(vimeo.get("available").asBoolean()).isTrue();
        assertThat(vimeo.toString()).doesNotContain("<script").doesNotContain("<iframe");

        JsonNode spotify = ana.json(ana.get(
                previewPath("https://open.spotify.com/intl-pt/track/4uLU6hMCjMI75M1A2tKUQC")));
        assertThat(spotify.get("embedProvider").asString()).isEqualTo("SPOTIFY");
        assertThat(spotify.get("embedSrc").asString())
                .isEqualTo("https://open.spotify.com/embed/track/4uLU6hMCjMI75M1A2tKUQC?utm_source=oembed");
        assertThat(spotify.get("available").asBoolean()).isTrue();

        JsonNode foreign = ana.json(ana.get(previewPath("https://vimeo.com/666")));
        assertThat(foreign.get("embedProvider").isNull()).as("iframe fora de player.vimeo.com").isTrue();
        assertThat(foreign.get("embedSrc").isNull()).isTrue();
    }

    @Test
    @DisplayName("site que goteja não prende a busca além do prazo total nem segura conexão do banco (MD-04)")
    void shouldBoundSlowFetchesWithoutHoldingConnections() throws Exception {
        String url = "http://127.0.0.1:" + SITE.getAddress().getPort() + "/goteja";
        var pool = dataSource.unwrap(com.zaxxer.hikari.HikariDataSource.class).getHikariPoolMXBean();
        int parallel = 12;
        long start = System.nanoTime();
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var results = new java.util.ArrayList<java.util.concurrent.Future<JsonNode>>();
            for (int i = 0; i < parallel; i++) {
                String each = url + "?n=" + i;
                results.add(executor.submit(() -> ana.json(ana.get(previewPath(each)))));
            }
            Thread.sleep(1500);
            assertThat(pool.getActiveConnections()).as("conexões presas durante as buscas").isLessThanOrEqualTo(1);
            for (var result : results) {
                assertThat(result.get(30, java.util.concurrent.TimeUnit.SECONDS).get("available").asBoolean())
                        .isFalse();
            }
        }
        long seconds = java.time.Duration.ofNanos(System.nanoTime() - start).toSeconds();
        // 4 buscas por vez no mesmo site (setMaxConnPerRoute) e prazo de 3 s: 3 rodadas, bem antes dos 40 s.
        assertThat(seconds).as("tempo total das prévias em gotejamento").isLessThan(15);
    }

    private static String previewPath(String url) {
        // O MockMvc codifica o template da URL; codificar aqui daria codificação dupla.
        return "/api/v1/link-previews?url=" + url;
    }
}
