package io.github.labdev2025cmd.papinho.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

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

/** Busca de mensagens com unaccent, pg_trgm e a configuração portuguese (GR-06). */
@IntegrationTest
class SearchIT {

    @Autowired
    private ApiClients clients;

    @Autowired
    private JdbcTemplate jdbc;

    private ApiClient ana;
    private ApiClient bruno;
    private String brunoId;
    private String conversation;

    @BeforeEach
    void setUp() {
        ana = clients.create();
        Accounts.register(ana, "ana", null);
        bruno = clients.create();
        brunoId = Accounts.userId(Accounts.register(bruno, "bruno", Accounts.invite(ana)));
        bruno.post("/api/v1/contacts", Map.of("username", "ana"));
        conversation = ana.json(ana.post("/api/v1/conversations/direct", Map.of("username", "bruno")))
                .get("id").asString();
    }

    @Test
    @DisplayName("busca global sem acento, por radical e por trecho; só nas minhas conversas")
    void shouldSearchGlobally() {
        String acao = send(ana, conversation, "A reunião de ação foi ótima");
        String conversas = send(bruno, conversation, "Adorei as conversas de ontem");
        String cidade = send(ana, conversation, "Vamos para Fortaleza em julho");
        send(ana, conversation, "nada a ver");

        assertThat(global(ana, "acao")).containsExactly(acao);
        assertThat(global(ana, "REUNIAO")).containsExactly(acao);
        assertThat(global(ana, "conversa")).as("radical em português").containsExactly(conversas);
        assertThat(global(ana, "Fortal")).as("trecho de palavra").containsExactly(cidade);
        assertThat(global(ana, "100%")).as("curinga escapado").isEmpty();
        assertThat(global(bruno, "fortaleza")).containsExactly(cidade);

        ApiClient carla = clients.create();
        Accounts.register(carla, "carla", Accounts.invite(ana));
        assertThat(global(carla, "fortaleza")).as("conversa dos outros").isEmpty();
        assertThat(ana.get("/api/v1/search/messages?q=a")).as("consulta curta").hasStatus(400);
    }

    @Test
    @DisplayName("apagadas, escondidas para mim e de conversa trancada ficam fora da busca global")
    void shouldSkipHiddenDeletedAndLocked() {
        String deleted = send(ana, conversation, "segredo apagado");
        String hidden = send(bruno, conversation, "segredo escondido");
        send(bruno, conversation, "segredo trancado");
        assertThat(ana.delete("/api/v1/messages/" + deleted, null)).hasStatusOk();
        assertThat(ana.post("/api/v1/messages/hide", Map.of("messageIds", List.of(hidden)))).hasStatus(204);
        assertThat(global(ana, "segredo")).hasSize(1);
        assertThat(global(bruno, "segredo")).as("o Bruno ainda vê a que a Ana escondeu").hasSize(2);

        assertThat(ana.put("/api/v1/privacy/locked-conversations/" + conversation, Map.of("code", "4321")))
                .hasStatusOk();
        assertThat(global(ana, "segredo")).as("trancada sem token").isEmpty();
        assertThat(ana.get("/api/v1/conversations/" + conversation + "/search?q=segredo")).hasStatus(403);
    }

    @Test
    @DisplayName("dentro da conversa filtra por texto, remetente, data e tipo, com páginas; IDOR 404")
    void shouldSearchInsideConversation() {
        String first = send(ana, conversation, "bom dia, grupo");
        String fromBruno = send(bruno, conversation, "bom dia! veja https://exemplo.com.br");
        String last = send(ana, conversation, "bom dia de novo");
        jdbc.update("update messages set created_at = now() - interval '3 days' where id = ?", UUID.fromString(first));

        assertThat(inside(ana, "q=bom+dia")).containsExactly(last, fromBruno, first);
        assertThat(inside(ana, "q=bom+dia&senderId=" + brunoId)).containsExactly(fromBruno);
        assertThat(inside(ana, "type=LINKS")).containsExactly(fromBruno);
        assertThat(inside(ana, "type=MEDIA")).isEmpty();
        String since = Instant.now().minusSeconds(86_400).toString();
        assertThat(inside(ana, "q=bom&from=" + since)).containsExactly(last, fromBruno);
        String until = Instant.now().minusSeconds(86_400).toString();
        assertThat(inside(ana, "to=" + until)).containsExactly(first);

        JsonNode page = ana.json(ana.get(path() + "?q=bom&limit=2"));
        assertThat(page.get("hasMore").asBoolean()).isTrue();
        long oldest = page.get("messages").get(1).get("seq").asLong();
        assertThat(inside(ana, "q=bom&limit=2&beforeSeq=" + oldest)).containsExactly(first);

        ApiClient carla = clients.create();
        Accounts.register(carla, "carla", Accounts.invite(ana));
        assertThat(carla.get(path() + "?q=bom")).hasStatus(404);
    }

    private List<String> global(ApiClient client, String query) {
        var result = client.get("/api/v1/search/messages?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
        assertThat(result).as(ApiClient.text(result)).hasStatusOk();
        return ids(client.json(result));
    }

    private List<String> inside(ApiClient client, String params) {
        var result = client.get(path() + "?" + params);
        assertThat(result).as(ApiClient.text(result)).hasStatusOk();
        return ids(client.json(result).get("messages"));
    }

    private String path() {
        return "/api/v1/conversations/" + conversation + "/search";
    }

    private String send(ApiClient client, String conversationId, String body) {
        var result = client.post("/api/v1/conversations/" + conversationId + "/messages",
                Map.of("clientMessageId", UUID.randomUUID(), "body", body));
        assertThat(result).hasStatus(201);
        return client.json(result).get("id").asString();
    }

    private static List<String> ids(JsonNode nodes) {
        return StreamSupport.stream(nodes.spliterator(), false).map(n -> n.get("id").asString()).toList();
    }
}
