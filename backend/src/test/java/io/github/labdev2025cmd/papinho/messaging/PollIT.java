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

/** Enquetes (MS-09): escolha única e múltipla, anônima, encerramento e resultados pelo afterSeq. */
@IntegrationTest
class PollIT {

    @Autowired
    private ApiClients clients;

    @Autowired
    private JdbcTemplate jdbc;

    private ApiClient ana;
    private ApiClient bruno;
    private ApiClient carla;
    private String anaId;
    private String group;

    @BeforeEach
    void setUp() {
        ana = clients.create();
        anaId = Accounts.userId(Accounts.register(ana, "ana", null));
        bruno = clients.create();
        String brunoId = Accounts.userId(Accounts.register(bruno, "bruno", Accounts.invite(ana)));
        carla = clients.create();
        String carlaId = Accounts.userId(Accounts.register(carla, "carla", Accounts.invite(ana)));
        ana.post("/api/v1/contacts", Map.of("username", "bruno"));
        ana.post("/api/v1/contacts", Map.of("username", "carla"));
        group = ana.json(ana.post("/api/v1/groups", Map.of("name", "Votação", "memberIds",
                List.of(brunoId, carlaId)))).get("id").asString();
    }

    @Test
    @DisplayName("escolha única: troca de voto, resultados para todos pelo afterSeq e quem votou em cada opção")
    void shouldVoteSingleChoice() {
        JsonNode poll = createPoll(ana, "Pizza ou sushi?", List.of("Pizza", "Sushi"), false, false, null);
        String id = poll.get("id").asString();
        assertThat(poll.get("kind").asString()).isEqualTo("POLL");
        assertThat(poll.get("body").asString()).isEqualTo("Pizza ou sushi?");
        String pizza = poll.get("poll").get("options").get(0).get("id").asString();
        String sushi = poll.get("poll").get("options").get(1).get("id").asString();

        long cursor = bruno.json(bruno.get(messages() + "?afterSeq=0")).get("lastSeq").asLong();
        JsonNode voted = bruno.json(bruno.put(vote(id), Map.of("optionIds", List.of(pizza))));
        assertThat(voted.get("poll").get("myVotes").get(0).asString()).isEqualTo(pizza);
        carla.put(vote(id), Map.of("optionIds", List.of(pizza)));
        assertThat(bruno.put(vote(id), Map.of("optionIds", List.of(pizza, sushi)))).as("escolha única")
                .hasStatus(400);
        bruno.put(vote(id), Map.of("optionIds", List.of(sushi)));

        JsonNode changed = ana.json(ana.get(messages() + "?afterSeq=" + cursor)).get("messages");
        JsonNode result = changed.get(changed.size() - 1).get("poll");
        assertThat(result.get("totalVoters").asLong()).isEqualTo(2);
        assertThat(result.get("options").get(0).get("votes").asLong()).isEqualTo(1);
        assertThat(result.get("options").get(1).get("voters").get(0).asString()).isNotBlank();
        assertThat(result.get("myVotes")).isEmpty();
        assertThat(ana.patch("/api/v1/messages/" + id, Map.of("body", "x"))).as("enquete não se edita")
                .hasStatus(400);
        assertThat(ana.post("/api/v1/messages/forward", Map.of("messageIds", List.of(id), "conversationIds",
                List.of(group)))).hasStatus(400);
    }

    @Test
    @DisplayName("múltipla e anônima esconde quem votou; encerrada recusa votos; validação das opções")
    void shouldHandleAnonymousMultipleAndClosing() {
        JsonNode poll = createPoll(ana, "Quais dias?", List.of("Sábado", "Domingo", "Feriado"), true, true,
                Instant.now().plusSeconds(3600));
        String id = poll.get("id").asString();
        List<String> options = poll.get("poll").get("options").findValuesAsString("id");
        JsonNode voted = bruno.json(bruno.put(vote(id), Map.of("optionIds", options.subList(0, 2))));
        assertThat(voted.get("poll").get("myVotes")).hasSize(2);
        JsonNode seenByAna = ana.json(ana.get(messages() + "?afterSeq=0")).get("messages");
        JsonNode anonymous = seenByAna.get(seenByAna.size() - 1).get("poll");
        assertThat(anonymous.get("options").get(0).get("votes").asLong()).isEqualTo(1);
        assertThat(anonymous.get("options").get(0).get("voters")).as("anônima").isEmpty();

        assertThat(bruno.put(vote(id), Map.of("optionIds", List.of()))).hasStatusOk();
        jdbc.update("update polls set closes_at = now() - interval '1 second' where message_id = ?",
                UUID.fromString(id));
        assertThat(carla.put(vote(id), Map.of("optionIds", options.subList(0, 1)))).hasStatus(409);

        assertThat(ana.post(path() + "/polls", Map.of("clientMessageId", UUID.randomUUID(), "question", "Só uma?",
                "options", List.of("Única")))).hasStatus(400);
        assertThat(ana.post(path() + "/polls", Map.of("clientMessageId", UUID.randomUUID(), "question", "Repete?",
                "options", List.of("Sim", "Sim")))).hasStatus(400);
        ApiClient outsider = clients.create();
        Accounts.register(outsider, "dani", Accounts.invite(ana));
        assertThat(outsider.put(vote(id), Map.of("optionIds", List.of()))).hasStatus(404);
        assertThat(anaId).isNotBlank();
    }

    @Test
    @DisplayName("na conversa individual com bloqueio, quem foi bloqueado não vota nem reage (CT-03)")
    void shouldRefuseVoteAndReactionWhenBlocked() {
        bruno.post("/api/v1/contacts", Map.of("username", "ana"));
        String direct = ana.json(ana.post("/api/v1/conversations/direct", Map.of("username", "bruno")))
                .get("id").asString();
        var created = ana.post("/api/v1/conversations/" + direct + "/polls", Map.of("clientMessageId",
                UUID.randomUUID(), "question", "Cinema?", "options", List.of("sim", "não"), "multiple", false,
                "anonymous", false));
        assertThat(created).hasStatus(201);
        String poll = ana.json(created).get("id").asString();
        String option = ana.json(created).get("poll").get("options").get(0).get("id").asString();
        String brunoId = bruno.json(bruno.get("/api/v1/me")).get("account").get("id").asString();

        assertThat(ana.put("/api/v1/blocks/" + brunoId, null)).hasStatus(204);
        var vote = bruno.put(vote(poll), Map.of("optionIds", List.of(option)));
        assertThat(vote).as("voto de quem foi bloqueado").hasStatus(403);
        assertThat(bruno.json(vote).get("type").asString()).isEqualTo("urn:papinho:problem:message-blocked");
        assertThat(bruno.put("/api/v1/messages/" + poll + "/reaction", Map.of("emoji", "👍")))
                .as("reação de quem foi bloqueado").hasStatus(403);
        assertThat(ana.put(vote(poll), Map.of("optionIds", List.of(option)))).as("nem quem bloqueou")
                .hasStatus(403);
        assertThat(jdbc.queryForObject("select count(*) from message_reactions", Integer.class)).isZero();

        // Em grupo o bloqueio não impede de participar (como no WhatsApp).
        JsonNode inGroup = createPoll(ana, "No grupo?", List.of("a", "b"), false, false, null);
        assertThat(bruno.put(vote(inGroup.get("id").asString()), Map.of("optionIds",
                List.of(inGroup.get("poll").get("options").get(0).get("id").asString())))).hasStatusOk();
    }

    private JsonNode createPoll(ApiClient client, String question, List<String> options, boolean multiple,
            boolean anonymous, Instant closesAt) {
        Map<String, Object> body = new HashMap<>();
        body.put("clientMessageId", UUID.randomUUID());
        body.put("question", question);
        body.put("options", options);
        body.put("multiple", multiple);
        body.put("anonymous", anonymous);
        body.put("closesAt", closesAt == null ? null : closesAt.toString());
        var result = client.post(path() + "/polls", body);
        assertThat(result).as(ApiClient.text(result)).hasStatus(201);
        return client.json(result);
    }

    private String path() {
        return "/api/v1/conversations/" + group;
    }

    private String messages() {
        return path() + "/messages";
    }

    private static String vote(String id) {
        return "/api/v1/messages/" + id + "/vote";
    }
}
