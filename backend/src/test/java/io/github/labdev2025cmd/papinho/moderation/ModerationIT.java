package io.github.labdev2025cmd.papinho.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.labdev2025cmd.papinho.support.Accounts;
import io.github.labdev2025cmd.papinho.support.ApiClient;
import io.github.labdev2025cmd.papinho.support.IntegrationTest;
import io.github.labdev2025cmd.papinho.support.TestSupportConfiguration.ApiClients;
import tools.jackson.databind.JsonNode;

/** Denúncias com cópia das últimas mensagens e a fila de moderação do admin (CT-04, AD-01). */
@IntegrationTest
class ModerationIT {

    @Autowired
    private ApiClients clients;

    private ApiClient admin;
    private ApiClient bruno;
    private ApiClient carla;
    private String conversation;

    @BeforeEach
    void setUp() {
        admin = clients.create();
        Accounts.register(admin, "ana", null);
        bruno = clients.create();
        Accounts.register(bruno, "bruno", Accounts.invite(admin));
        carla = clients.create();
        Accounts.register(carla, "carla", Accounts.invite(admin));
        var opened = carla.post("/api/v1/conversations/direct", Map.of("username", "bruno"));
        conversation = carla.json(opened).get("id").asString();
    }

    @Test
    @DisplayName("denúncia copia as 5 últimas mensagens, bloqueia junto e entra na fila do admin")
    void shouldReportWithLastMessagesAndBlock() {
        for (int i = 1; i <= 7; i++) {
            send(i % 2 == 0 ? carla : bruno, "mensagem " + i);
        }
        var reported = bruno.post("/api/v1/reports",
                Map.of("conversationId", conversation, "reason", "spam", "block", true));
        assertThat(reported).as(ApiClient.text(reported)).hasStatus(201);

        JsonNode blocks = bruno.json(bruno.get("/api/v1/blocks"));
        assertThat(blocks.get(0).get("username").asString()).isEqualTo("carla");
        assertThat(send(carla, "ainda aí?")).as("bloqueado não envia").isEqualTo(403);

        JsonNode queue = admin.json(admin.get("/api/v1/admin/reports"));
        assertThat(queue.get("openCount").asLong()).isEqualTo(1);
        JsonNode report = queue.get("items").get(0);
        assertThat(report.get("reporter").get("username").asString()).isEqualTo("bruno");
        assertThat(report.get("reported").get("username").asString()).isEqualTo("carla");
        assertThat(report.get("reason").asString()).isEqualTo("spam");
        assertThat(bodies(report.get("messages")))
                .containsExactly("mensagem 3", "mensagem 4", "mensagem 5", "mensagem 6", "mensagem 7");
        assertThat(report.get("messages").get(1).get("fromReported").asBoolean()).isTrue();

        assertThat(admin.post("/api/v1/admin/reports/" + report.get("id").asString() + "/resolve", null))
                .hasStatus(204);
        assertThat(admin.json(admin.get("/api/v1/admin/reports")).get("openCount").asLong()).isZero();
        JsonNode closed = admin.json(admin.get("/api/v1/admin/reports?open=false")).get("items").get(0);
        assertThat(closed.get("status").asString()).isEqualTo("RESOLVED");
    }

    @Test
    @DisplayName("avisos do sistema e mensagens apagadas não tomam o lugar das 5 últimas na cópia (CT-04)")
    void shouldCopyFiveUserMessagesSkippingNoticesAndDeleted() {
        for (int i = 1; i <= 9; i++) {
            send(i % 2 == 0 ? carla : bruno, "m" + i);
        }
        String path = "/api/v1/conversations/" + conversation;
        assertThat(carla.put(path + "/disappearing", Map.of("seconds", 86_400))).hasStatus(204);
        Map<String, Object> off = new HashMap<>();
        off.put("seconds", null);
        assertThat(carla.put(path + "/disappearing", off)).hasStatus(204);
        String deleted = carla.json(carla.post(path + "/messages",
                Map.of("clientMessageId", UUID.randomUUID(), "body", "m10"))).get("id").asString();
        assertThat(carla.delete("/api/v1/messages/" + deleted, null)).hasStatus(200);
        send(carla, "m11");

        assertThat(bruno.post("/api/v1/reports", Map.of("conversationId", conversation, "reason", "de novo",
                "block", false))).hasStatus(201);
        JsonNode report = admin.json(admin.get("/api/v1/admin/reports")).get("items").get(0);
        assertThat(bodies(report.get("messages"))).containsExactly("m6", "m7", "m8", "m9", "m11");
    }

    @Test
    @DisplayName("só quem participa denuncia, e só o admin vê a fila")
    void shouldProtectReportsAndQueue() {
        send(carla, "oi");
        assertThat(admin.post("/api/v1/reports", Map.of("conversationId", conversation, "block", false)))
                .as("conversa de outros (IDOR)").hasStatus(404);
        assertThat(admin.post("/api/v1/reports", Map.of("conversationId", UUID.randomUUID(), "block", false)))
                .hasStatus(404);
        assertThat(bruno.get("/api/v1/admin/reports")).hasStatus(403);

        var withoutBlock = bruno.post("/api/v1/reports", Map.of("conversationId", conversation, "block", false));
        assertThat(withoutBlock).hasStatus(201);
        assertThat(bruno.json(bruno.get("/api/v1/blocks"))).isEmpty();
        String id = admin.json(admin.get("/api/v1/admin/reports")).get("items").get(0).get("id").asString();
        assertThat(bruno.post("/api/v1/admin/reports/" + id + "/dismiss", null)).hasStatus(403);
        assertThat(admin.post("/api/v1/admin/reports/" + id + "/dismiss", null)).hasStatus(204);
        assertThat(admin.json(admin.get("/api/v1/admin/reports?open=false")).get("items").get(0).get("status")
                .asString()).isEqualTo("DISMISSED");
    }

    private int send(ApiClient client, String body) {
        Map<String, Object> request = new HashMap<>();
        request.put("clientMessageId", UUID.randomUUID());
        request.put("body", body);
        return client.post("/api/v1/conversations/" + conversation + "/messages", request).getResponse()
                .getStatus();
    }

    private static List<String> bodies(JsonNode messages) {
        return StreamSupport.stream(messages.spliterator(), false).map(n -> n.get("body").asString()).toList();
    }
}
