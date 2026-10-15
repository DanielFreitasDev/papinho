package io.github.labdev2025cmd.papinho.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
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

/** Mensagens agendadas, envio silencioso e rascunho sincronizado (MS-11). */
@IntegrationTest
class ScheduledIT {

    @Autowired
    private ApiClients clients;

    @Autowired
    private ScheduledMessageService scheduled;

    @Autowired
    private JdbcTemplate jdbc;

    private ApiClient ana;
    private ApiClient bruno;
    private String anaId;
    private String conversation;

    @BeforeEach
    void setUp() {
        ana = clients.create();
        anaId = Accounts.userId(Accounts.register(ana, "ana", null));
        bruno = clients.create();
        Accounts.register(bruno, "bruno", Accounts.invite(ana));
        bruno.post("/api/v1/contacts", Map.of("username", "ana"));
        conversation = ana.json(ana.post("/api/v1/conversations/direct", Map.of("username", "bruno")))
                .get("id").asString();
    }

    @Test
    @DisplayName("agendada sai na hora como silenciosa; só quem agendou vê e cancela; hora no passado dá 400")
    void shouldSendScheduledMessage() {
        assertThat(ana.post(path() + "/scheduled", Map.of("body", "ontem", "sendAt",
                Instant.now().minusSeconds(60).toString()))).hasStatus(400);
        JsonNode created = ana.json(ana.post(path() + "/scheduled", Map.of("body", "Parabéns!", "sendAt",
                Instant.now().plusSeconds(3600).toString(), "silent", true)));
        String id = created.get("id").asString();
        assertThat(ana.json(ana.get(path() + "/scheduled"))).hasSize(1);
        assertThat(bruno.json(bruno.get(path() + "/scheduled"))).as("só quem agendou").isEmpty();
        assertThat(bruno.delete("/api/v1/scheduled/" + id, null)).hasStatus(404);

        jdbc.update("update scheduled_messages set send_at = now() - interval '1 second' where id = ?",
                UUID.fromString(id));
        scheduled.dispatchDue(10);
        JsonNode sent = bruno.json(bruno.get(path() + "/messages")).get("messages");
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).get("body").asString()).isEqualTo("Parabéns!");
        assertThat(sent.get(0).get("silent").asBoolean()).isTrue();
        assertThat(sent.get(0).get("senderId").asString()).isEqualTo(anaId);
        assertThat(ana.json(ana.get(path() + "/scheduled"))).isEmpty();

        String cancelled = ana.json(ana.post(path() + "/scheduled", Map.of("body", "talvez", "sendAt",
                Instant.now().plusSeconds(3600).toString()))).get("id").asString();
        assertThat(ana.delete("/api/v1/scheduled/" + cancelled, null)).hasStatus(204);
        assertThat(ana.json(ana.get(path() + "/scheduled"))).isEmpty();
    }

    @Test
    @DisplayName("agendada que não pode sair (bloqueio) fica marcada como falha")
    void shouldMarkFailedWhenBlocked() {
        String id = ana.json(ana.post(path() + "/scheduled", Map.of("body", "oi", "sendAt",
                Instant.now().plusSeconds(3600).toString()))).get("id").asString();
        assertThat(bruno.put("/api/v1/blocks/" + anaId, null)).hasStatus(204);
        jdbc.update("update scheduled_messages set send_at = now() - interval '1 second' where id = ?",
                UUID.fromString(id));
        scheduled.dispatchDue(10);
        JsonNode list = ana.json(ana.get(path() + "/scheduled"));
        assertThat(list.get(0).get("failedAt").isNull()).isFalse();
        assertThat(bruno.json(bruno.get(path() + "/messages")).get("messages")).isEmpty();
    }

    @Test
    @DisplayName("rascunho fica na conversa para os meus dispositivos, não para a outra pessoa, e some ao enviar")
    void shouldSyncDraft() {
        assertThat(ana.put(path() + "/draft", Map.of("body", "pensando no que dizer"))).hasStatus(204);
        assertThat(ana.json(ana.get(path())).get("draft").asString()).isEqualTo("pensando no que dizer");
        assertThat(bruno.get(path())).as("conversa vazia ainda não aparece para o Bruno").hasStatus(200);
        assertThat(bruno.json(bruno.get(path())).get("draft").isNull()).isTrue();
        ana.post(path() + "/messages", Map.of("clientMessageId", UUID.randomUUID(), "body", "Oi!"));
        assertThat(ana.json(ana.get(path())).get("draft").isNull()).as("enviar apaga o rascunho").isTrue();
        assertThat(ana.put(path() + "/draft", Map.of("body", "outro"))).hasStatus(204);
        assertThat(ana.put(path() + "/draft", Map.of("body", ""))).hasStatus(204);
        assertThat(ana.json(ana.get(path())).get("draft").isNull()).isTrue();
    }

    private String path() {
        return "/api/v1/conversations/" + conversation;
    }
}
