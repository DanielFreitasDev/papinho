package io.github.labdev2025cmd.papinho.messaging;

import static org.assertj.core.api.Assertions.assertThat;

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

/** Organização da lista de conversas: arquivar, fixar, silenciar, favoritar, pastas, limpar e apagar (GR-05). */
@IntegrationTest
class ChatListIT {

    @Autowired
    private ApiClients clients;

    private ApiClient ana;
    private ApiClient bruno;

    @BeforeEach
    void setUp() {
        ana = clients.create();
        Accounts.register(ana, "ana", null);
        bruno = clients.create();
        Accounts.register(bruno, "bruno", Accounts.invite(ana));
        bruno.post("/api/v1/contacts", Map.of("username", "ana"));
    }

    @Test
    @DisplayName("arquivada sai do arquivo com mensagem nova, salvo 'manter arquivadas'; só quem arquivou vê")
    void shouldArchive() {
        String conversation = open(ana, "bruno");
        send(ana, conversation, "oi");
        assertThat(ana.put(path(conversation) + "/archive", null)).hasStatus(204);
        assertThat(organization(ana, conversation).get("archived").asBoolean()).isTrue();
        assertThat(organization(bruno, conversation).get("archived").asBoolean()).as("só para a Ana").isFalse();

        send(ana, conversation, "minha própria mensagem não desarquiva");
        assertThat(organization(ana, conversation).get("archived").asBoolean()).isTrue();
        send(bruno, conversation, "resposta");
        assertThat(organization(ana, conversation).get("archived").asBoolean()).as("mensagem nova desarquiva")
                .isFalse();

        assertThat(ana.put("/api/v1/messaging/preferences",
                Map.of("keepArchived", true))).hasStatusOk();
        assertThat(ana.json(ana.get("/api/v1/messaging/preferences")).get("keepArchived").asBoolean()).isTrue();
        ana.put(path(conversation) + "/archive", null);
        send(bruno, conversation, "mais uma");
        assertThat(organization(ana, conversation).get("archived").asBoolean()).as("manter arquivadas").isTrue();
        assertThat(ana.delete(path(conversation) + "/archive", null)).hasStatus(204);
        assertThat(organization(ana, conversation).get("archived").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("fixa até 3 conversas no topo; a quarta dá 409 e arquivar solta")
    void shouldPinUpToThree() {
        String bruno = open(ana, "bruno");
        send(ana, bruno, "oi");
        List<String> others = List.of("carla", "davi", "edu").stream().map(name -> {
            Accounts.register(clients.create(), name, Accounts.invite(ana));
            String id = open(ana, name);
            send(ana, id, "oi " + name);
            return id;
        }).toList();
        assertThat(ids(ana.json(ana.get("/api/v1/conversations"))).getFirst()).isEqualTo(others.get(2));

        assertThat(ana.put(path(bruno) + "/pin", null)).hasStatus(204);
        assertThat(ids(ana.json(ana.get("/api/v1/conversations"))).getFirst()).as("fixada no topo").isEqualTo(bruno);
        assertThat(ana.put(path(others.get(0)) + "/pin", null)).hasStatus(204);
        assertThat(ana.put(path(others.get(1)) + "/pin", null)).hasStatus(204);
        assertThat(ana.put(path(bruno) + "/pin", null)).as("fixar de novo não conta duas vezes").hasStatus(204);
        var fourth = ana.put(path(others.get(2)) + "/pin", null);
        assertThat(fourth).hasStatus(409);
        assertThat(ana.json(fourth).get("type").asString()).isEqualTo("urn:papinho:problem:conversation-pin-limit");

        ana.put(path(bruno) + "/archive", null);
        assertThat(organization(ana, bruno).get("pinnedAt").isNull()).as("arquivar solta").isTrue();
        assertThat(ana.put(path(others.get(2)) + "/pin", null)).hasStatus(204);
        assertThat(ana.delete(path(others.get(2)) + "/pin", null)).hasStatus(204);
        assertThat(organization(ana, others.get(2)).get("pinnedAt").isNull()).isTrue();
    }

    @Test
    @DisplayName("sair e voltar a um grupo fixado não fura o limite de 3 fixadas (GR-05)")
    void shouldNotExceedPinLimitAfterLeavingAndRejoiningGroup() {
        String brunoId = bruno.json(bruno.get("/api/v1/auth/session")).get("me").get("account").get("id")
                .asString();
        ana.post("/api/v1/contacts", Map.of("username", "bruno"));
        String group = ana.json(ana.post("/api/v1/groups", Map.of("name", "Fixado", "memberIds",
                List.of(brunoId)))).get("id").asString();
        String code = ana.json(ana.get("/api/v1/groups/" + group + "/invite")).get("code").asString();
        List<String> directs = List.of("carla", "davi", "edu").stream().map(name -> {
            Accounts.register(clients.create(), name, Accounts.invite(ana));
            String id = open(bruno, name);
            send(bruno, id, "oi " + name);
            return id;
        }).toList();
        assertThat(bruno.put(path(group) + "/pin", null)).hasStatus(204);
        assertThat(bruno.put(path(directs.get(0)) + "/pin", null)).hasStatus(204);
        assertThat(bruno.put(path(directs.get(1)) + "/pin", null)).hasStatus(204);
        assertThat(bruno.put(path(directs.get(2)) + "/pin", null)).hasStatus(409);

        assertThat(bruno.post("/api/v1/groups/" + group + "/leave", null)).hasStatus(204);
        assertThat(bruno.put(path(directs.get(2)) + "/pin", null)).hasStatus(204);
        assertThat(bruno.post("/api/v1/groups/invites/" + code + "/join", null)).hasStatusOk();
        long pinned = StreamSupport.stream(bruno.json(bruno.get("/api/v1/conversations")).spliterator(), false)
                .filter(item -> !item.get("organization").get("pinnedAt").isNull()).count();
        assertThat(pinned).as("fixadas na lista depois de voltar ao grupo").isEqualTo(3);
        assertThat(organization(bruno, group).get("pinnedAt").isNull()).as("o grupo volta sem fixar").isTrue();
    }

    @Test
    @DisplayName("silencia por 8 h, 1 semana ou sempre, com menções; favorita; quem é de fora recebe 404")
    void shouldMuteAndFavorite() {
        String conversation = open(ana, "bruno");
        send(ana, conversation, "oi");
        assertThat(ana.put(path(conversation) + "/mute", Map.of("duration", "EIGHT_HOURS", "mentions", true)))
                .hasStatus(204);
        JsonNode muted = organization(ana, conversation);
        assertThat(java.time.Instant.parse(muted.get("mutedUntil").asString()))
                .isBetween(java.time.Instant.now().plusSeconds(8 * 3600 - 60),
                        java.time.Instant.now().plusSeconds(8 * 3600 + 60));
        assertThat(muted.get("muteMentions").asBoolean()).isTrue();
        ana.put(path(conversation) + "/mute", Map.of("duration", "ALWAYS"));
        assertThat(organization(ana, conversation).get("mutedUntil").asString()).startsWith("9999-12-31");
        assertThat(organization(ana, conversation).get("muteMentions").asBoolean()).isFalse();
        assertThat(ana.put(path(conversation) + "/mute", Map.of("duration", "FOREVER"))).hasStatus(400);
        ana.put(path(conversation) + "/mute", java.util.Collections.singletonMap("duration", null));
        assertThat(organization(ana, conversation).get("mutedUntil").isNull()).isTrue();
        assertThat(organization(bruno, conversation).get("mutedUntil").isNull()).isTrue();

        assertThat(ana.put(path(conversation) + "/favorite", null)).hasStatus(204);
        assertThat(organization(ana, conversation).get("favorite").asBoolean()).isTrue();
        assertThat(organization(bruno, conversation).get("favorite").asBoolean()).isFalse();

        ApiClient carla = clients.create();
        Accounts.register(carla, "carla", Accounts.invite(ana));
        for (String action : List.of("/favorite", "/pin", "/archive")) {
            assertThat(carla.put(path(conversation) + action, null)).as(action).hasStatus(404);
        }
        assertThat(carla.put(path(conversation) + "/mute", Map.of("duration", "ALWAYS"))).hasStatus(404);
        assertThat(carla.post(path(conversation) + "/clear", null)).hasStatus(404);
        assertThat(carla.delete(path(conversation), null)).hasStatus(404);
    }

    @Test
    @DisplayName("pastas só com conversas minhas; IDOR 404; apagar a pasta não apaga as conversas")
    void shouldManageFolders() {
        String conversation = open(ana, "bruno");
        send(ana, conversation, "oi");
        var created = ana.post("/api/v1/folders", Map.of("name", " Trabalho ", "conversationIds",
                List.of(conversation)));
        assertThat(created).hasStatus(201);
        JsonNode folder = ana.json(created);
        String folderId = folder.get("id").asString();
        assertThat(folder.get("name").asString()).isEqualTo("Trabalho");
        assertThat(organization(ana, conversation).get("folderIds").get(0).asString()).isEqualTo(folderId);
        assertThat(organization(bruno, conversation).get("folderIds")).isEmpty();
        assertThat(ana.post("/api/v1/folders", Map.of("name", " ", "conversationIds", List.of()))).hasStatus(400);

        ApiClient carla = clients.create();
        Accounts.register(carla, "carla", Accounts.invite(ana));
        assertThat(carla.post("/api/v1/folders", Map.of("name", "Minha", "conversationIds", List.of(conversation))))
                .as("conversa dos outros").hasStatus(404);
        assertThat(carla.json(carla.get("/api/v1/folders"))).isEmpty();
        assertThat(carla.put("/api/v1/folders/" + folderId, Map.of("name", "x", "conversationIds", List.of())))
                .hasStatus(404);
        assertThat(carla.delete("/api/v1/folders/" + folderId, null)).hasStatus(404);

        assertThat(ana.delete("/api/v1/folders/" + folderId + "/conversations/" + conversation, null)).hasStatusOk();
        assertThat(organization(ana, conversation).get("folderIds")).isEmpty();
        assertThat(ana.put("/api/v1/folders/" + folderId + "/conversations/" + conversation, null)).hasStatusOk();
        assertThat(ana.put("/api/v1/folders/" + folderId, Map.of("name", "Família", "conversationIds",
                List.of(conversation)))).hasStatusOk();
        assertThat(ana.json(ana.get("/api/v1/folders")).get(0).get("name").asString()).isEqualTo("Família");
        assertThat(ana.delete("/api/v1/folders/" + folderId, null)).hasStatus(204);
        assertThat(organization(ana, conversation).get("folderIds")).isEmpty();
        assertThat(ids(ana.json(ana.get("/api/v1/conversations")))).contains(conversation);
    }

    @Test
    @DisplayName("limpar some com as mensagens só para mim (mantendo favoritas); apagar tira da lista até a próxima")
    void shouldClearAndRemove() {
        String conversation = open(ana, "bruno");
        String starred = send(ana, conversation, "guardar");
        send(bruno, conversation, "tchau");
        ana.put("/api/v1/messages/" + starred + "/star", null);

        assertThat(ana.post(path(conversation) + "/clear", null)).hasStatus(204);
        JsonNode left = ana.json(ana.get(path(conversation) + "/messages")).get("messages");
        assertThat(ids(left)).containsExactly(starred);
        assertThat(bruno.json(bruno.get(path(conversation) + "/messages")).get("messages")).as("só para a Ana")
                .hasSize(2);
        assertThat(ana.post(path(conversation) + "/clear", Map.of("keepStarred", false))).hasStatus(204);
        assertThat(ana.json(ana.get(path(conversation) + "/messages")).get("messages")).isEmpty();
        assertThat(ana.json(ana.get("/api/v1/messages/starred"))).isEmpty();

        ana.put(path(conversation) + "/pin", null);
        assertThat(ana.delete(path(conversation), null)).hasStatus(204);
        assertThat(ids(ana.json(ana.get("/api/v1/conversations")))).doesNotContain(conversation);
        assertThat(ids(bruno.json(bruno.get("/api/v1/conversations")))).contains(conversation);
        send(bruno, conversation, "voltei");
        assertThat(ids(ana.json(ana.get("/api/v1/conversations")))).as("mensagem nova traz de volta")
                .contains(conversation);
        assertThat(organization(ana, conversation).get("pinnedAt").isNull()).isTrue();
        assertThat(ana.json(ana.get(path(conversation) + "/messages")).get("messages")).hasSize(1);
    }

    private JsonNode organization(ApiClient client, String conversation) {
        var result = client.get(path(conversation));
        assertThat(result).hasStatusOk();
        return client.json(result).get("organization");
    }

    private static String open(ApiClient client, String username) {
        var result = client.post("/api/v1/conversations/direct", Map.of("username", username));
        assertThat(result).hasStatusOk();
        return client.json(result).get("id").asString();
    }

    private static String send(ApiClient client, String conversation, String body) {
        var result = client.post(path(conversation) + "/messages",
                Map.of("clientMessageId", UUID.randomUUID(), "body", body));
        assertThat(result).hasStatus(201);
        return client.json(result).get("id").asString();
    }

    private static String path(String conversation) {
        return "/api/v1/conversations/" + conversation;
    }

    private static List<String> ids(JsonNode nodes) {
        return StreamSupport.stream(nodes.spliterator(), false).map(n -> n.get("id").asString()).toList();
    }
}
