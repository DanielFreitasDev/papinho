package io.github.labdev2025cmd.papinho.transcription;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import com.sun.net.httpserver.HttpServer;

import io.github.labdev2025cmd.papinho.support.Accounts;
import io.github.labdev2025cmd.papinho.support.ApiClient;
import io.github.labdev2025cmd.papinho.support.IntegrationTest;
import io.github.labdev2025cmd.papinho.support.TestSupportConfiguration.ApiClients;
import io.github.labdev2025cmd.papinho.support.Uploads;
import tools.jackson.databind.JsonNode;

/**
 * Transcrição com provedor compatível com a OpenAI (AU-05) simulado localmente e cache para os demais
 * membros (AU-03).
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class TranscriptionIT {

    private static final String SECRET = "gsk_segredo_que_nao_pode_vazar";
    private static final HttpServer PROVIDER;
    private static final AtomicInteger CALLS = new AtomicInteger();
    private static final AtomicReference<Map<String, String>> LAST = new AtomicReference<>(Map.of());
    private static volatile int status = 200;

    static {
        try {
            PROVIDER = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        PROVIDER.createContext("/openai/v1/audio/transcriptions", exchange -> {
            CALLS.incrementAndGet();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1);
            Map<String, String> seen = new HashMap<>();
            seen.put("authorization", String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            seen.put("userAgent", String.valueOf(exchange.getRequestHeaders().getFirst("User-Agent")));
            seen.put("contentType", String.valueOf(exchange.getRequestHeaders().getFirst("Content-Type")));
            seen.put("body", body);
            LAST.set(seen);
            byte[] answer = status == 200 ? "{\"text\":\" Oi, tudo bem? \"}".getBytes(StandardCharsets.UTF_8)
                    : ("{\"error\":\"bad key " + SECRET + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, answer.length);
            exchange.getResponseBody().write(answer);
            exchange.close();
        });
        PROVIDER.createContext("/health", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        PROVIDER.start();
    }

    @AfterAll
    static void stop() {
        PROVIDER.stop(0);
    }

    @Autowired
    private ApiClients clients;

    @Autowired
    private JdbcTemplate jdbc;

    private ApiClient admin;
    private ApiClient bruno;
    private ApiClient carla;
    private String conversation;
    private String audio;

    @BeforeEach
    void setUp() {
        status = 200;
        admin = clients.create();
        Accounts.register(admin, "ana", null);
        bruno = clients.create();
        Accounts.register(bruno, "bruno", Accounts.invite(admin));
        carla = clients.create();
        Accounts.register(carla, "carla", Accounts.invite(admin));
        conversation = admin.json(admin.post("/api/v1/conversations/direct", Map.of("username", "bruno")))
                .get("id").asString();
        audio = Uploads.upload(admin, Uploads.ogg(2048), "voz.ogg", "audio/ogg;codecs=opus");
        Uploads.send(admin, conversation, audio, "");
    }

    @Test
    @DisplayName("admin configura Groq; quem recebe transcreve uma vez e o texto fica em cache, sem vazar a chave")
    void shouldTranscribeThroughProviderAndCache(CapturedOutput output) {
        String provider = configureProvider();
        JsonNode adminView = admin.json(admin.get("/api/v1/admin/transcription"));
        assertThat(adminView.get("providers").get(0).get("hasKey").asBoolean()).isTrue();
        assertThat(ApiClient.text(admin.get("/api/v1/admin/transcription"))).doesNotContain(SECRET);
        assertThat(bruno.get("/api/v1/admin/transcription")).hasStatus(403);
        String stored = jdbc.queryForObject("select api_key_encrypted from transcription_providers where id = ?",
                String.class, UUID.fromString(provider));
        assertThat(stored).as("chave cifrada em repouso (AU-05)").startsWith("v1:").doesNotContain(SECRET);

        int before = CALLS.get();
        String path = "/api/v1/media/" + audio + "/transcript";
        assertThat(bruno.get(path)).hasStatus(204);
        JsonNode transcript = bruno.json(bruno.post(path, null));
        assertThat(transcript.get("text").asString()).isEqualTo("Oi, tudo bem?");
        assertThat(transcript.get("source").asString()).isEqualTo("GROQ");
        Map<String, String> request = LAST.get();
        assertThat(request.get("authorization")).isEqualTo("Bearer " + SECRET);
        assertThat(request.get("userAgent")).startsWith("Papinho/").doesNotStartWith("OpenAI/");
        assertThat(request.get("contentType")).startsWith("multipart/form-data");
        assertThat(request.get("body")).contains("name=\"model\"\r\n\r\nwhisper-large-v3-turbo")
                .contains("name=\"file\"; filename=\"voz.ogg\"").contains("OggS");

        assertThat(admin.json(admin.get(path)).get("text").asString()).as("cache para o autor").isEqualTo(
                "Oi, tudo bem?");
        assertThat(admin.json(admin.post(path, null)).get("text").asString()).isEqualTo("Oi, tudo bem?");
        assertThat(CALLS.get() - before).as("provedor chamado uma vez só").isEqualTo(1);
        assertThat(carla.get(path)).as("fora da conversa").hasStatus(404);
        assertThat(carla.post(path, null)).hasStatus(404);
        assertThat(admin.post("/api/v1/admin/transcription/providers/" + provider + "/health", null))
                .hasStatusOk();
        assertThat(output.getAll()).doesNotContain(SECRET);
    }

    @Test
    @DisplayName("sem provedor no servidor a transcrição é do navegador; o texto enviado vira cache")
    void shouldFallBackToBrowser(CapturedOutput output) {
        String path = "/api/v1/media/" + audio + "/transcript";
        var conflict = bruno.post(path, null);
        assertThat(conflict).hasStatus(409);
        assertThat(bruno.json(conflict).get("provider").asString()).isEqualTo("BROWSER");
        assertThat(bruno.put(path, Map.of("text", "transcrito no navegador"))).hasStatusOk();
        assertThat(admin.json(admin.get(path)).get("source").asString()).isEqualTo("BROWSER");

        configureProvider();
        status = 401;
        String other = Uploads.upload(admin, Uploads.ogg(1024), "outra.ogg", "audio/ogg");
        Uploads.send(admin, conversation, other, "");
        assertThat(bruno.post("/api/v1/media/" + other + "/transcript", null)).hasStatus(502);
        assertThat(output.getAll()).doesNotContain(SECRET);

        JsonNode options = bruno.json(bruno.get("/api/v1/transcription/options"));
        assertThat(options.get("providers")).hasSize(1);
        assertThat(bruno.put("/api/v1/transcription/preference", Map.of("provider", "BROWSER", "auto", true)))
                .hasStatusOk();
        assertThat(bruno.post("/api/v1/media/" + other + "/transcript", null)).as("preferência pelo navegador")
                .hasStatus(409);
    }

    @Test
    @DisplayName("trocar o endereço do provedor sem informar a chave apaga a chave: ela não vai ao destino novo")
    void shouldDropKeyWhenProviderOriginChanges() {
        String provider = configureProvider();
        Map<String, Object> update = new HashMap<>();
        update.put("name", "Groq");
        update.put("model", "whisper-large-v3-turbo");
        update.put("maxBytes", 25 * 1024 * 1024);
        update.put("enabled", true);
        update.put("baseUrl", "http://127.0.0.1:" + PROVIDER.getAddress().getPort() + "/openai/");
        JsonNode samePlace = admin.json(admin.put("/api/v1/admin/transcription/providers/" + provider, update));
        assertThat(samePlace.get("hasKey").asBoolean()).as("mesma origem, só o caminho").isTrue();

        update.put("baseUrl", "http://localhost:" + PROVIDER.getAddress().getPort() + "/openai");
        JsonNode moved = admin.json(admin.put("/api/v1/admin/transcription/providers/" + provider, update));
        assertThat(moved.get("hasKey").asBoolean()).as("outro host").isFalse();
        assertThat(jdbc.queryForObject("select api_key_encrypted from transcription_providers where id = ?",
                String.class, UUID.fromString(provider))).isNull();
        assertThat(bruno.post("/api/v1/media/" + audio + "/transcript", null)).hasStatusOk();
        assertThat(LAST.get().get("authorization")).as("destino novo sem a chave antiga").isEqualTo("null");

        update.put("apiKey", SECRET);
        update.put("baseUrl", "http://127.0.0.1:" + PROVIDER.getAddress().getPort() + "/openai");
        assertThat(admin.json(admin.put("/api/v1/admin/transcription/providers/" + provider, update))
                .get("hasKey").asBoolean()).as("chave informada junto com o endereço").isTrue();
    }

    private String configureProvider() {
        String id = admin.json(admin.post("/api/v1/admin/transcription/providers", Map.of("kind", "GROQ")))
                .get("id").asString();
        Map<String, Object> update = new HashMap<>();
        update.put("name", "Groq");
        update.put("baseUrl", "http://127.0.0.1:" + PROVIDER.getAddress().getPort() + "/openai");
        update.put("model", "whisper-large-v3-turbo");
        update.put("maxBytes", 25 * 1024 * 1024);
        update.put("enabled", true);
        update.put("apiKey", SECRET);
        assertThat(admin.put("/api/v1/admin/transcription/providers/" + id, update)).hasStatusOk();
        assertThat(admin.put("/api/v1/admin/transcription/default", Map.of("providerId", id))).hasStatus(204);
        return id;
    }
}
