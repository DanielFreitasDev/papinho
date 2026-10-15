package io.github.labdev2025cmd.papinho.shared.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import io.github.labdev2025cmd.papinho.TestcontainersConfiguration;
import io.github.labdev2025cmd.papinho.support.Accounts;
import io.github.labdev2025cmd.papinho.support.DatabaseCleaner;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * WebSocket de verdade (ADR 0004): handshake só com sessão, CSRF no CONNECT, avisos de mensagens e leitura,
 * "digitando…" (MS-03), presença com aba visível (RT-03) e fechamento quando a sessão HTTP acaba.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@TestExecutionListeners(listeners = DatabaseCleaner.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
class RealtimeIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    private int port;

    @Autowired
    private RealtimeSessions sessions;

    private WebSocketStompClient stomp;

    @BeforeEach
    void setUp() {
        stomp = new WebSocketStompClient(new StandardWebSocketClient());
        stomp.setMessageConverter(new JacksonJsonMessageConverter());
    }

    @AfterEach
    void tearDown() {
        stomp.stop();
    }

    @Test
    @DisplayName("handshake sem sessão é recusado e CONNECT sem token CSRF não abre a sessão STOMP")
    void shouldRequireSessionAndCsrf() throws Exception {
        assertThatThrownBy(() -> stomp.connectAsync(ws(), new WebSocketHttpHeaders(), new StompHeaders(),
                new StompSessionHandlerAdapter() {
                }).get(5, TimeUnit.SECONDS)).hasMessageContaining("401");

        Browser ana = Browser.register(base(), "ana", null);
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Cookie", ana.cookieHeader());
        BlockingQueue<String> errors = new LinkedBlockingQueue<>();
        StompSessionHandlerAdapter recorder = new StompSessionHandlerAdapter() {
            @Override
            public void handleException(StompSession session, @Nullable StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                errors.add(String.valueOf(exception));
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                errors.add(String.valueOf(exception));
            }
        };
        StompHeaders wrongToken = new StompHeaders();
        wrongToken.add("X-XSRF-TOKEN", "token-errado");
        for (StompHeaders connect : java.util.List.of(new StompHeaders(), wrongToken)) {
            assertThatThrownBy(() -> stomp.connectAsync(ws(), headers, connect, recorder).get(5, TimeUnit.SECONDS))
                    .as("CONNECT sem token CSRF válido").isNotNull();
        }
        assertThat(errors).as("o servidor fecha a conexão nos dois casos").hasSize(2)
                .allMatch(error -> error.contains("Connection closed"));
        assertThat(connect(ana).session().isConnected()).as("com o token, conecta").isTrue();
    }

    @Test
    @DisplayName("mensagem nova, leitura e digitando chegam pela fila do usuário")
    void shouldNotifyMessagesReceiptsAndTyping() throws Exception {
        Browser ana = Browser.register(base(), "ana", null);
        Browser bruno = Browser.register(base(), "bruno", ana.invite());
        bruno.post("/api/v1/contacts", Map.of("username", "ana"));
        String conversation = ana.post("/api/v1/conversations/direct", Map.of("username", "bruno")).get("id")
                .asString();
        Connection anaWs = connect(ana);
        Connection brunoWs = connect(bruno);

        ana.post("/api/v1/conversations/" + conversation + "/messages",
                Map.of("clientMessageId", UUID.randomUUID(), "body", "Oi"));
        JsonNode event = brunoWs.next("messages");
        assertThat(event.get("conversationId").asString()).isEqualTo(conversation);
        assertThat(event.get("lastSeq").asLong()).isEqualTo(1);

        bruno.put("/api/v1/conversations/" + conversation + "/read", Map.of("seq", 1));
        JsonNode receipt = anaWs.next("receipts");
        assertThat(receipt.get("readSeq").asLong()).isEqualTo(1);
        assertThat(receipt.get("userId").asString()).isEqualTo(bruno.userId());

        anaWs.session().send("/app/activity", Map.of("conversationId", conversation, "activity", "typing"));
        JsonNode typing = brunoWs.next("activity");
        assertThat(typing.get("activity").asString()).isEqualTo("typing");
        assertThat(typing.get("userId").asString()).isEqualTo(ana.userId());
    }

    @Test
    @DisplayName("presença: online só com aba visível; ao sair, quem conversa recebe offline e visto por último")
    void shouldTrackPresence() throws Exception {
        Browser ana = Browser.register(base(), "ana", null);
        Browser bruno = Browser.register(base(), "bruno", ana.invite());
        bruno.post("/api/v1/contacts", Map.of("username", "ana"));
        String conversation = ana.post("/api/v1/conversations/direct", Map.of("username", "bruno")).get("id")
                .asString();
        ana.post("/api/v1/conversations/" + conversation + "/messages",
                Map.of("clientMessageId", UUID.randomUUID(), "body", "Oi"));
        Connection anaWs = connect(ana);
        Connection brunoWs = connect(bruno);

        brunoWs.session().send("/app/presence", Map.of("active", false));
        assertThat(ana.get("/api/v1/presence/" + bruno.userId()).get("online").asBoolean())
                .as("aba em segundo plano não conta").isFalse();
        brunoWs.session().send("/app/presence", Map.of("active", true));
        assertThat(anaWs.next("presence").get("online").asBoolean()).isTrue();
        assertThat(ana.get("/api/v1/presence/" + bruno.userId()).get("online").asBoolean()).isTrue();

        brunoWs.session().disconnect();
        JsonNode offline = anaWs.next("presence");
        assertThat(offline.get("online").asBoolean()).isFalse();
        assertThat(offline.get("lastSeenAt").isNull()).isFalse();

        Browser carla = Browser.register(base(), "carla", ana.invite());
        assertThat(carla.status("/api/v1/presence/" + bruno.userId())).as("sem conversa, sem presença")
                .isEqualTo(404);
    }

    @Test
    @DisplayName("logout derruba o WebSocket aberto com aquela sessão")
    void shouldCloseWebSocketAfterLogout() throws Exception {
        Browser ana = Browser.register(base(), "ana", null);
        Connection anaWs = connect(ana);
        assertThat(anaWs.session().isConnected()).isTrue();

        ana.post("/api/v1/auth/logout", null);
        sessions.closeOrphans();
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (anaWs.session().isConnected() && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertThat(anaWs.session().isConnected()).isFalse();
    }

    private Connection connect(Browser browser) throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Cookie", browser.cookieHeader());
        StompHeaders connect = new StompHeaders();
        connect.add("X-XSRF-TOKEN", browser.cookies.get("XSRF-TOKEN"));
        BlockingQueue<JsonNode> events = new LinkedBlockingQueue<>();
        StompSession session = stomp.connectAsync(ws(), headers, connect, new StompSessionHandlerAdapter() {
        }).get(5, TimeUnit.SECONDS);
        StompSession.Subscription subscription = session.subscribe("/user/queue/events", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, @Nullable Object payload) {
                events.add(JSON.readTree((byte[]) payload));
            }
        });
        assertThat(subscription).isNotNull();
        // A assinatura é assíncrona: espera o broker registrar antes de gerar eventos.
        Thread.sleep(300);
        return new Connection(session, events);
    }

    private String base() {
        return "http://localhost:" + port;
    }

    private String ws() {
        return "ws://localhost:" + port + "/ws";
    }

    private record Connection(StompSession session, BlockingQueue<JsonNode> events) {

        /** Próximo evento do tipo pedido, ignorando os outros (presença, conversa). */
        JsonNode next(String type) throws InterruptedException {
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (System.nanoTime() < deadline) {
                JsonNode event = events.poll(200, TimeUnit.MILLISECONDS);
                if (event != null && type.equals(event.get("type").asString())) {
                    return event;
                }
            }
            throw new AssertionError("No '" + type + "' event received");
        }
    }

    /** Navegador mínimo: guarda os cookies (inclusive {@code __Host-}) e manda o CSRF como o SPA. */
    private static final class Browser {

        private final HttpClient http = HttpClient.newHttpClient();
        private final String base;
        private final Map<String, String> cookies = new HashMap<>();
        private String userId = "";

        private Browser(String base) {
            this.base = base;
        }

        static Browser register(String base, String username, @Nullable String invite) throws Exception {
            Browser browser = new Browser(base);
            browser.get("/api/v1/instance");
            Map<String, Object> body = new HashMap<>();
            body.put("username", username);
            body.put("displayName", "Pessoa " + username);
            body.put("password", Accounts.PASSWORD);
            if (invite != null) {
                body.put("inviteToken", invite);
            }
            browser.userId = browser.post("/api/v1/auth/register", body).get("me").get("account").get("id")
                    .asString();
            return browser;
        }

        String userId() {
            return userId;
        }

        String invite() throws Exception {
            String url = post("/api/v1/invites", Map.of("validHours", 1, "maxUses", 5)).get("url").asString();
            return url.substring(url.indexOf("invite=") + "invite=".length());
        }

        JsonNode get(String path) throws Exception {
            return JSON.readTree(send(HttpRequest.newBuilder(URI.create(base + path)).GET(), 200).body());
        }

        int status(String path) throws Exception {
            return send(HttpRequest.newBuilder(URI.create(base + path)).GET(), -1).statusCode();
        }

        JsonNode post(String path, @Nullable Object body) throws Exception {
            return write("POST", path, body);
        }

        JsonNode put(String path, @Nullable Object body) throws Exception {
            return write("PUT", path, body);
        }

        String cookieHeader() {
            StringBuilder header = new StringBuilder();
            cookies.forEach((name, value) -> header.append(header.isEmpty() ? "" : "; ").append(name).append('=')
                    .append(value));
            return header.toString();
        }

        private JsonNode write(String method, String path, @Nullable Object body) throws Exception {
            String json = body == null ? "" : JSON.writeValueAsString(body);
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path))
                    .method(method, HttpRequest.BodyPublishers.ofString(json))
                    .header("Content-Type", "application/json")
                    .header("X-XSRF-TOKEN", cookies.getOrDefault("XSRF-TOKEN", ""));
            String responseBody = send(request, -1).body();
            return responseBody.isBlank() ? JSON.nullNode() : JSON.readTree(responseBody);
        }

        private HttpResponse<String> send(HttpRequest.Builder request, int expected) throws Exception {
            if (!cookies.isEmpty()) {
                request.header("Cookie", cookieHeader());
            }
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            response.headers().allValues("Set-Cookie").forEach(this::store);
            if (expected > 0) {
                assertThat(response.statusCode()).as(response.body()).isEqualTo(expected);
            } else {
                assertThat(response.statusCode()).as(response.body()).isLessThan(500);
            }
            return response;
        }

        private void store(String setCookie) {
            String pair = setCookie.split(";", 2)[0];
            int equals = pair.indexOf('=');
            String name = pair.substring(0, equals);
            String value = pair.substring(equals + 1);
            if (value.isEmpty() || setCookie.contains("Max-Age=0")) {
                cookies.remove(name);
            } else {
                cookies.put(name, value);
            }
        }
    }
}
