package io.github.labdev2025cmd.papinho.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.labdev2025cmd.papinho.support.Accounts;
import io.github.labdev2025cmd.papinho.support.ApiClient;
import io.github.labdev2025cmd.papinho.support.IntegrationTest;
import io.github.labdev2025cmd.papinho.support.TestSupportConfiguration.ApiClients;
import tools.jackson.databind.JsonNode;

/** Listas de transmissão (GR-03) e temporárias (GR-04). */
@IntegrationTest
class BroadcastIT {

    @Autowired
    private ApiClients clients;

    @Autowired
    private JdbcTemplate jdbc;

    private ApiClient ana;
    private ApiClient bruno;
    private ApiClient carla;
    private String brunoId;
    private String carlaId;

    @BeforeEach
    void setUp() {
        ana = clients.create();
        Accounts.register(ana, "ana", null);
        bruno = clients.create();
        brunoId = Accounts.userId(Accounts.register(bruno, "bruno", Accounts.invite(ana)));
        carla = clients.create();
        carlaId = Accounts.userId(Accounts.register(carla, "carla", Accounts.invite(ana)));
        ana.post("/api/v1/contacts", Map.of("username", "bruno"));
        ana.post("/api/v1/contacts", Map.of("username", "carla"));
        // Só o Bruno tem a Ana nos contatos.
        bruno.post("/api/v1/contacts", Map.of("username", "ana"));
    }

    @Test
    @DisplayName("envio chega como conversa individual só para quem tem o remetente nos contatos")
    void shouldFanOutToContactsOnly() {
        ApiClient dani = clients.create();
        String daniId = Accounts.userId(Accounts.register(dani, "dani", Accounts.invite(ana)));
        assertThat(ana.post("/api/v1/broadcasts", Map.of("name", "Avisos", "recipientIds", List.of(daniId))))
                .as("só contatos").hasStatus(403);

        JsonNode list = ana.json(ana.post("/api/v1/broadcasts",
                Map.of("name", "Avisos", "recipientIds", List.of(brunoId, carlaId))));
        String id = list.get("id").asString();
        assertThat(list.get("kind").asString()).isEqualTo("BROADCAST");
        assertThat(list.get("broadcast").get("recipientCount").asLong()).isEqualTo(2);
        assertThat(ana.json(ana.get("/api/v1/broadcasts/" + id + "/recipients"))).hasSize(2);
        assertThat(bruno.get("/api/v1/conversations/" + id)).as("lista é só do dono").hasStatus(404);
        assertThat(bruno.get("/api/v1/broadcasts/" + id + "/recipients")).hasStatus(404);

        send(ana, id, "Reunião amanhã às 10h");
        JsonNode brunoList = bruno.json(bruno.get("/api/v1/conversations"));
        assertThat(brunoList).hasSize(1);
        assertThat(brunoList.get(0).get("kind").asString()).isEqualTo("DIRECT");
        assertThat(brunoList.get(0).get("lastMessage").get("body").asString()).isEqualTo("Reunião amanhã às 10h");
        assertThat(carla.json(carla.get("/api/v1/conversations"))).as("sem a Ana nos contatos").isEmpty();
        assertThat(carla.json(carla.get("/api/v1/conversations?requests=true"))).isEmpty();
        assertThat(ana.json(ana.get("/api/v1/conversations/" + id + "/messages")).get("messages")).hasSize(1);

        Map<String, Object> poll = new HashMap<>();
        poll.put("clientMessageId", UUID.randomUUID());
        poll.put("question", "Vem?");
        poll.put("options", List.of("Sim", "Não"));
        assertThat(ana.post("/api/v1/conversations/" + id + "/polls", poll)).hasStatus(400);
    }

    @Test
    @DisplayName("lista temporária: mensagens com timer e, depois da data, a lista não envia mais (GR-04)")
    void shouldHandleTemporaryLists() {
        JsonNode list = ana.json(ana.post("/api/v1/broadcasts", Map.of("name", "Festa", "recipientIds",
                List.of(brunoId), "expiresAt", Instant.now().plusSeconds(3600).toString(), "messageTtlSeconds",
                86_400)));
        String id = list.get("id").asString();
        assertThat(list.get("broadcast").get("messageTtlSeconds").asInt()).isEqualTo(86_400);
        send(ana, id, "Traga bebida");
        JsonNode received = bruno.json(bruno.get("/api/v1/conversations")).get(0);
        JsonNode copy = bruno.json(bruno.get("/api/v1/conversations/" + received.get("id").asString()
                + "/messages")).get("messages").get(0);
        assertThat(copy.get("expiresAt").isNull()).as("cópia temporária").isFalse();

        jdbc.update("update broadcast_details set expires_at = now() - interval '1 second' where conversation_id = ?",
                UUID.fromString(id));
        JsonNode expired = ana.json(ana.get("/api/v1/conversations/" + id));
        assertThat(expired.get("broadcast").get("expired").asBoolean()).isTrue();
        assertThat(expired.get("canSend").asBoolean()).isFalse();
        assertThat(ana.post("/api/v1/conversations/" + id + "/messages",
                Map.of("clientMessageId", UUID.randomUUID(), "body", "tarde"))).hasStatus(403);

        var reopen = ana.put("/api/v1/broadcasts/" + id, Map.of("name", "Festa", "recipientIds", List.of(brunoId),
                "expiresAt", Instant.now().plusSeconds(86_400).toString()));
        assertThat(reopen).as("lista vencida não reabre").hasStatus(403);
        assertThat(ana.json(reopen).get("type").asString()).isEqualTo("urn:papinho:problem:broadcast-expired");
        assertThat(ana.json(ana.get("/api/v1/conversations/" + id)).get("broadcast").get("expired").asBoolean())
                .isTrue();
    }

    @Test
    @DisplayName("editar a lista com validade no passado é recusado, como na criação (GR-04)")
    void shouldRefusePastExpiryOnUpdate() {
        String id = ana.json(ana.post("/api/v1/broadcasts", Map.of("name", "Festa", "recipientIds",
                List.of(brunoId)))).get("id").asString();
        assertThat(ana.put("/api/v1/broadcasts/" + id, Map.of("name", "Festa", "recipientIds", List.of(brunoId),
                "expiresAt", "2020-01-01T00:00:00Z"))).hasStatus(400);
        assertThat(ana.json(ana.get("/api/v1/conversations/" + id)).get("broadcast").get("expiresAt").isNull())
                .isTrue();
    }

    private static void send(ApiClient client, String conversation, String body) {
        var result = client.post("/api/v1/conversations/" + conversation + "/messages",
                Map.of("clientMessageId", UUID.randomUUID(), "body", body));
        assertThat(result).as(ApiClient.text(result)).hasStatus(201);
    }
}
