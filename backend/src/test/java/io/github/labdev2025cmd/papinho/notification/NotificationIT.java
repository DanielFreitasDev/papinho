package io.github.labdev2025cmd.papinho.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.labdev2025cmd.papinho.notification.internal.WebPushCrypto;
import io.github.labdev2025cmd.papinho.support.Accounts;
import io.github.labdev2025cmd.papinho.support.ApiClient;
import io.github.labdev2025cmd.papinho.support.IntegrationTest;
import io.github.labdev2025cmd.papinho.support.RecordingPushGateway;
import io.github.labdev2025cmd.papinho.support.TestSupportConfiguration.ApiClients;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Notificações decididas no servidor (NT-01, NT-03) com privacidade (PV-03, PV-08) e silêncio (GR-05, MS-11). */
@IntegrationTest
class NotificationIT {

    private static final String BRUNO_PUSH = "https://fcm.googleapis.com/fcm/send/bruno-1";

    @Autowired
    private ApiClients clients;

    @Autowired
    private RecordingPushGateway push;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JsonMapper json;

    private ApiClient ana;
    private ApiClient bruno;
    private String conversation;

    @BeforeEach
    void setUp() {
        push.clear();
        ana = clients.create();
        Accounts.register(ana, "ana", null);
        bruno = clients.create();
        Accounts.register(bruno, "bruno", Accounts.invite(ana));
        bruno.post("/api/v1/contacts", Map.of("username", "ana"));
        conversation = ana.json(ana.post("/api/v1/conversations/direct", Map.of("username", "bruno")))
                .get("id").asString();
    }

    @Test
    @DisplayName("preferências começam ligadas e são só da própria pessoa")
    void shouldKeepPreferences() {
        JsonNode defaults = bruno.json(bruno.get("/api/v1/notifications/preferences"));
        for (String field : List.of("enabled", "direct", "groups", "mentions", "sound")) {
            assertThat(defaults.get(field).asBoolean()).as(field).isTrue();
        }
        assertThat(bruno.put("/api/v1/notifications/preferences", Map.of("enabled", true, "direct", false,
                "groups", true, "mentions", true, "sound", false))).hasStatusOk();
        JsonNode changed = bruno.json(bruno.get("/api/v1/notifications/preferences"));
        assertThat(changed.get("direct").asBoolean()).isFalse();
        assertThat(changed.get("sound").asBoolean()).isFalse();
        assertThat(ana.json(ana.get("/api/v1/notifications/preferences")).get("direct").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("assinatura só de serviço de push conhecido, por HTTPS e com chave P-256 válida (sem SSRF)")
    void shouldValidateSubscriptions() throws Exception {
        JsonNode info = bruno.json(bruno.get("/api/v1/notifications/push"));
        assertThat(info.get("enabled").asBoolean()).isTrue();
        assertThat(Base64.getUrlDecoder().decode(info.get("publicKey").asString())).hasSize(65);
        assertThat(ana.json(ana.get("/api/v1/notifications/push")).get("publicKey").asString())
                .as("uma chave por instância").isEqualTo(info.get("publicKey").asString());

        String key = browserKey();
        for (String endpoint : List.of("http://fcm.googleapis.com/fcm/send/x", "https://evil.example/push",
                "https://fcm.googleapis.com.evil.example/x", "https://user@fcm.googleapis.com/x",
                "https://fcm.googleapis.com:8443/x", "https://127.0.0.1/x")) {
            assertThat(bruno.post("/api/v1/notifications/push/subscriptions", subscription(endpoint, key)))
                    .as(endpoint).hasStatus(400);
        }
        assertThat(bruno.post("/api/v1/notifications/push/subscriptions",
                subscription(BRUNO_PUSH, "BAAA"))).as("chave inválida").hasStatus(400);
        assertThat(bruno.post("/api/v1/notifications/push/subscriptions", subscription(
                "https://updates.push.services.mozilla.com/wpush/v2/abc", key))).hasStatus(204);
        assertThat(bruno.post("/api/v1/notifications/push/subscriptions", subscription(BRUNO_PUSH, key)))
                .hasStatus(204);
        assertThat(ana.delete("/api/v1/notifications/push/subscriptions", Map.of("endpoint", BRUNO_PUSH)))
                .hasStatus(204);
        assertThat(jdbc.queryForObject("select count(*) from push_subscriptions", Integer.class))
                .as("só a própria assinatura sai").isEqualTo(2);
        assertThat(bruno.delete("/api/v1/notifications/push/subscriptions", Map.of("endpoint", BRUNO_PUSH)))
                .hasStatus(204);
        assertThat(jdbc.queryForObject("select count(*) from push_subscriptions", Integer.class)).isEqualTo(1);
        String privateKey = jdbc.queryForObject("select private_key from push_vapid_keys", String.class);
        assertThat(privateKey).as("chave privada VAPID cifrada").startsWith("v1:");
    }

    @Test
    @DisplayName("avisa quem recebe com nome e texto; nunca quem enviou; silenciosa não avisa")
    void shouldNotifyRecipient() throws Exception {
        subscribe(bruno, BRUNO_PUSH);
        subscribe(ana, "https://fcm.googleapis.com/fcm/send/ana-1");
        send(ana, "Oi Bruno, tudo bem?", false);
        JsonNode sent = only(BRUNO_PUSH);
        assertThat(sent.get("title").asString()).isEqualTo("Pessoa ana");
        assertThat(sent.get("body").asString()).isEqualTo("Oi Bruno, tudo bem?");
        assertThat(sent.get("tag").asString()).isEqualTo("conversation-" + conversation);
        assertThat(sent.get("conversationId").asString()).isEqualTo(conversation);
        assertThat(sent.get("badge").asLong()).isEqualTo(1);
        assertThat(sent.get("silent").asBoolean()).isFalse();
        assertThat(push.sent()).as("quem enviou não é avisado").hasSize(1);

        push.clear();
        send(ana, "psiu", true);
        settle();
        assertThat(push.sent()).as("silenciosa (MS-11)").isEmpty();
    }

    @Test
    @DisplayName("ocultar conteúdo (PV-08) e conversa trancada (PV-03) mostram só 'Nova mensagem'")
    void shouldHideContent() throws Exception {
        subscribe(bruno, BRUNO_PUSH);
        assertThat(bruno.patch("/api/v1/privacy", Map.of("hideNotificationContent", true))).hasStatusOk();
        send(ana, "segredo", false);
        JsonNode hidden = only(BRUNO_PUSH);
        assertThat(hidden.get("title").asString()).isEqualTo("Papinho");
        assertThat(hidden.get("body").asString()).isEqualTo("Nova mensagem");
        assertThat(push.sent().getFirst().payload()).doesNotContain("segredo").doesNotContain("Pessoa ana");

        push.clear();
        bruno.patch("/api/v1/privacy", Map.of("hideNotificationContent", false));
        assertThat(bruno.put("/api/v1/privacy/locked-conversations/" + conversation, Map.of("code", "4321")))
                .hasStatusOk();
        send(ana, "outro segredo", false);
        JsonNode locked = only(BRUNO_PUSH);
        assertThat(locked.get("body").asString()).isEqualTo("Nova mensagem");
        assertThat(push.sent().getFirst().payload()).doesNotContain("segredo");
    }

    @Test
    @DisplayName("silenciada só avisa menção quando pedido; tipo desligado e solicitação de estranho não avisam")
    void shouldRespectMuteAndPreferences() throws Exception {
        subscribe(bruno, BRUNO_PUSH);
        bruno.put("/api/v1/conversations/" + conversation + "/mute", Map.of("duration", "ALWAYS"));
        send(ana, "ninguém ouve", false);
        settle();
        assertThat(push.sent()).as("silenciada").isEmpty();

        bruno.put("/api/v1/conversations/" + conversation + "/mute", Map.of("duration", "ALWAYS", "mentions", true));
        send(ana, "sem menção", false);
        settle();
        assertThat(push.sent()).isEmpty();
        send(ana, "olha isso @Bruno", false);
        assertThat(only(BRUNO_PUSH).get("body").asString()).isEqualTo("olha isso @Bruno");

        push.clear();
        bruno.put("/api/v1/conversations/" + conversation + "/mute", java.util.Collections.singletonMap("duration",
                null));
        bruno.put("/api/v1/notifications/preferences", Map.of("enabled", true, "direct", false, "groups", true,
                "mentions", true, "sound", true));
        send(ana, "individual desligada", false);
        settle();
        assertThat(push.sent()).as("individuais desligadas").isEmpty();

        bruno.put("/api/v1/notifications/preferences", Map.of("enabled", true, "direct", true, "groups", true,
                "mentions", true, "sound", true));
        ApiClient carla = clients.create();
        Accounts.register(carla, "carla", Accounts.invite(ana));
        String request = carla.json(carla.post("/api/v1/conversations/direct", Map.of("username", "bruno")))
                .get("id").asString();
        assertThat(carla.post("/api/v1/conversations/" + request + "/messages",
                Map.of("clientMessageId", UUID.randomUUID(), "body", "compre já"))).hasStatus(201);
        settle();
        assertThat(push.sent()).as("solicitação de quem não é contato (CT-02)").isEmpty();
    }

    @Test
    @DisplayName("assinatura vencida (410) é apagada")
    void shouldForgetGoneSubscriptions() throws Exception {
        subscribe(bruno, "https://fcm.googleapis.com/fcm/send/gone-1");
        send(ana, "oi", false);
        settle();
        assertThat(push.sent()).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from push_subscriptions", Integer.class)).isZero();
    }

    private JsonNode only(String endpoint) {
        await().atMost(Duration.ofSeconds(10)).until(() -> !push.sent().isEmpty());
        settle();
        List<RecordingPushGateway.Sent> sent = push.sent();
        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst().endpoint()).isEqualTo(endpoint);
        return json.readTree(sent.getFirst().payload());
    }

    /** Espera os ouvintes assíncronos terminarem: publicação concluída sai do registro de eventos. */
    private void settle() {
        await().atMost(Duration.ofSeconds(10)).until(() -> jdbc.queryForObject(
                "select count(*) from event_publication where completion_date is null", Integer.class) == 0);
    }

    private void send(ApiClient client, String body, boolean silent) {
        assertThat(client.post("/api/v1/conversations/" + conversation + "/messages",
                Map.of("clientMessageId", UUID.randomUUID(), "body", body, "silent", silent))).hasStatus(201);
    }

    private void subscribe(ApiClient client, String endpoint) throws Exception {
        assertThat(client.post("/api/v1/notifications/push/subscriptions", subscription(endpoint, browserKey())))
                .hasStatus(204);
    }

    private static Map<String, Object> subscription(String endpoint, String p256dh) {
        return Map.of("endpoint", endpoint, "keys", Map.of("p256dh", p256dh, "auth",
                Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16])));
    }

    private static String browserKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(WebPushCrypto.uncompressed((ECPublicKey) generator.generateKeyPair().getPublic()));
    }
}
