package io.github.labdev2025cmd.papinho.messaging;

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

/** Conversas individuais, sincronização por seq, idempotência, confirmações e ações de mensagem (Fase 2). */
@IntegrationTest
class MessagingIT {

    @Autowired
    private ApiClients clients;

    private ApiClient ana;
    private ApiClient bruno;
    private String anaId;
    private String brunoId;

    @BeforeEach
    void setUp() {
        ana = clients.create();
        anaId = Accounts.userId(Accounts.register(ana, "ana", null));
        bruno = clients.create();
        brunoId = Accounts.userId(Accounts.register(bruno, "bruno", Accounts.invite(ana)));
    }

    @Test
    @DisplayName("seq cresce por conversa, reenvio com o mesmo clientMessageId não duplica e afterSeq traz só o que faltou")
    void shouldSyncBySeqAndDeduplicate() {
        String conversation = open(ana, "bruno");
        UUID first = UUID.randomUUID();
        var created = ana.post(messages(conversation), Map.of("clientMessageId", first, "body", "Oi *Bruno*"));
        assertThat(created).hasStatus(201);
        assertThat(ana.json(created).get("seq").asLong()).isEqualTo(1);

        var repeated = ana.post(messages(conversation), Map.of("clientMessageId", first, "body", "Oi *Bruno*"));
        assertThat(repeated).hasStatusOk();
        assertThat(ana.json(repeated).get("id").asString()).isEqualTo(ana.json(created).get("id").asString());
        send(ana, conversation, "Tudo bem?");

        JsonNode all = bruno.json(bruno.get(messages(conversation) + "?afterSeq=0"));
        assertThat(seqs(all.get("messages"))).containsExactly(1L, 2L);
        assertThat(all.get("lastSeq").asLong()).isEqualTo(2);
        JsonNode missing = bruno.json(bruno.get(messages(conversation) + "?afterSeq=1"));
        assertThat(seqs(missing.get("messages"))).containsExactly(2L);

        assertThat(ana.post(messages(conversation), Map.of("clientMessageId", UUID.randomUUID(), "body", "  ")))
                .hasStatus(400);
        String other = open(ana, "carla", true);
        assertThat(ana.post(messages(other), Map.of("clientMessageId", first, "body", "outra")))
                .as("clientMessageId de outra conversa").hasStatus(409);
    }

    @Test
    @DisplayName("histórico pagina para trás por beforeSeq em ordem crescente")
    void shouldPageHistoryBackwards() {
        String conversation = open(ana, "bruno");
        for (int i = 1; i <= 5; i++) {
            send(ana, conversation, "mensagem " + i);
        }
        JsonNode latest = ana.json(ana.get(messages(conversation) + "?limit=2"));
        assertThat(seqs(latest.get("messages"))).containsExactly(4L, 5L);
        assertThat(latest.get("hasMore").asBoolean()).isTrue();
        JsonNode older = ana.json(ana.get(messages(conversation) + "?limit=3&beforeSeq=4"));
        assertThat(seqs(older.get("messages"))).containsExactly(1L, 2L, 3L);
        assertThat(older.get("hasMore").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("quem não é contato cai em solicitações; antes de aceitar, quem enviou não vê a leitura (CT-02)")
    void shouldHandleMessageRequests() {
        String conversation = open(ana, "bruno");
        assertThat(bruno.json(bruno.get("/api/v1/conversations"))).as("vazia não aparece").isEmpty();
        send(ana, conversation, "Oi, sou a Ana");

        assertThat(bruno.json(bruno.get("/api/v1/conversations"))).isEmpty();
        JsonNode requests = bruno.json(bruno.get("/api/v1/conversations?requests=true"));
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).get("state").asString()).isEqualTo("REQUEST");
        assertThat(requests.get(0).get("unreadCount").asLong()).isEqualTo(1);

        assertThat(bruno.put(conversationPath(conversation) + "/read", Map.of("seq", 1))).hasStatus(204);
        JsonNode seenByAna = ana.json(ana.get(conversationPath(conversation)));
        assertThat(seenByAna.get("peerDeliveredSeq").asLong()).isEqualTo(1);
        assertThat(seenByAna.get("peerReadSeq").isNull()).as("leitura escondida antes de aceitar").isTrue();

        assertThat(bruno.post(conversationPath(conversation) + "/accept", null)).hasStatusOk();
        assertThat(ana.json(ana.get(conversationPath(conversation))).get("peerReadSeq").asLong()).isEqualTo(1);
        assertThat(bruno.json(bruno.get("/api/v1/conversations"))).hasSize(1);

        ApiClient carla = clients.create();
        Accounts.register(carla, "carla", Accounts.invite(ana));
        String fromCarla = open(carla, "bruno");
        send(carla, fromCarla, "Oi");
        assertThat(bruno.post(conversationPath(fromCarla) + "/decline", null)).hasStatus(204);
        assertThat(bruno.json(bruno.get("/api/v1/conversations?requests=true"))).isEmpty();
        assertThat(bruno.get(conversationPath(fromCarla))).hasStatus(404);
    }

    @Test
    @DisplayName("contato vira conversa direta; responder aceita; não lidas, marcar como não lida e Dados da mensagem")
    void shouldTrackReceiptsAndUnread() {
        assertThat(bruno.post("/api/v1/contacts", Map.of("username", "ana"))).hasStatus(201);
        String conversation = open(ana, "bruno");
        send(ana, conversation, "um");
        String second = send(ana, conversation, "dois");

        JsonNode list = bruno.json(bruno.get("/api/v1/conversations"));
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("state").asString()).isEqualTo("ACTIVE");
        assertThat(list.get(0).get("unreadCount").asLong()).isEqualTo(2);
        assertThat(list.get(0).get("firstUnreadSeq").asLong()).isEqualTo(1);
        assertThat(list.get(0).get("peer").get("label").asString()).isEqualTo("Pessoa ana");
        assertThat(list.get(0).get("lastMessage").get("body").asString()).isEqualTo("dois");

        assertThat(bruno.put(conversationPath(conversation) + "/delivered", Map.of("seq", 99))).hasStatus(204);
        assertThat(ana.json(ana.get(conversationPath(conversation))).get("peerDeliveredSeq").asLong())
                .as("ponteiro limitado ao último seq").isEqualTo(2);
        bruno.put(conversationPath(conversation) + "/read", Map.of("seq", 1));
        JsonNode afterRead = bruno.json(bruno.get(conversationPath(conversation)));
        assertThat(afterRead.get("unreadCount").asLong()).isEqualTo(1);
        assertThat(afterRead.get("firstUnreadSeq").asLong()).isEqualTo(2);

        assertThat(bruno.put(conversationPath(conversation) + "/unread", null)).hasStatus(204);
        assertThat(bruno.json(bruno.get(conversationPath(conversation))).get("markedUnread").asBoolean()).isTrue();
        bruno.put(conversationPath(conversation) + "/read", Map.of("seq", 2));
        JsonNode read = bruno.json(bruno.get(conversationPath(conversation)));
        assertThat(read.get("markedUnread").asBoolean()).isFalse();
        assertThat(read.get("unreadCount").asLong()).isZero();

        JsonNode info = ana.json(ana.get("/api/v1/messages/" + second + "/info"));
        JsonNode recipient = info.get("recipients").get(0);
        assertThat(recipient.get("username").asString()).isEqualTo("bruno");
        assertThat(recipient.get("deliveredAt").isNull()).isFalse();
        assertThat(recipient.get("readAt").isNull()).isFalse();
        assertThat(bruno.get("/api/v1/messages/" + second + "/info")).as("só o autor").hasStatus(404);

        String reply = bruno.json(bruno.post(messages(conversation), Map.of("clientMessageId", UUID.randomUUID(),
                "body", "respondendo", "replyToId", second))).get("replyTo").get("id").asString();
        assertThat(reply).isEqualTo(second);
        assertThat(ana.json(ana.get(conversationPath(conversation))).get("unreadCount").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("reagir, fixar (até 3), favoritar e encaminhar avançam o seq certo e contam os saltos")
    void shouldReactPinStarAndForward() {
        assertThat(bruno.post("/api/v1/contacts", Map.of("username", "ana"))).hasStatus(201);
        String conversation = open(ana, "bruno");
        String message = send(ana, conversation, "fixe isto");

        JsonNode reacted = bruno.json(bruno.put("/api/v1/messages/" + message + "/reaction", Map.of("emoji", "👍🏽")));
        assertThat(reacted.get("reactions").get(0).get("emoji").asString()).isEqualTo("👍🏽");
        assertThat(reacted.get("changeSeq").asLong()).isEqualTo(2);
        assertThat(bruno.put("/api/v1/messages/" + message + "/reaction", Map.of("emoji", "ok"))).hasStatus(400);
        JsonNode changes = ana.json(ana.get(messages(conversation) + "?afterSeq=1"));
        assertThat(changes.get("messages").get(0).get("id").asString()).as("reação chega pelo afterSeq")
                .isEqualTo(message);
        assertThat(bruno.json(bruno.delete("/api/v1/messages/" + message + "/reaction", null)).get("reactions"))
                .isEmpty();

        List<String> pinned = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            String id = send(ana, conversation, "fixada " + i);
            assertThat(ana.put("/api/v1/messages/" + id + "/pin", null)).hasStatusOk();
            pinned.add(id);
        }
        JsonNode pins = ana.json(ana.get(conversationPath(conversation) + "/pinned"));
        assertThat(pins).hasSize(3);
        assertThat(StreamSupport.stream(pins.spliterator(), false).map(n -> n.get("id").asString()))
                .as("a mais antiga foi solta").containsExactlyElementsOf(pinned.subList(1, 4));

        assertThat(bruno.json(bruno.put("/api/v1/messages/" + message + "/star", null)).get("starred").asBoolean())
                .isTrue();
        assertThat(bruno.json(bruno.get("/api/v1/messages/starred"))).hasSize(1);
        assertThat(ana.json(ana.get("/api/v1/messages/starred"))).as("favorita é só de quem marcou").isEmpty();

        String target = open(ana, "carla", true);
        String forwarded = message;
        for (int hop = 1; hop <= 5; hop++) {
            JsonNode result = ana.json(ana.post("/api/v1/messages/forward",
                    Map.of("messageIds", List.of(forwarded), "conversationIds", List.of(target))));
            assertThat(result.get(0).get("forwardCount").asInt()).isEqualTo(hop);
            assertThat(result.get(0).get("forwardedMany").asBoolean()).isEqualTo(hop >= 5);
            forwarded = result.get(0).get("id").asString();
        }
    }

    @Test
    @DisplayName("bloqueio impede mensagens nos dois sentidos; terceiros não acessam a conversa (IDOR)")
    void shouldEnforceBlocksAndMembership() {
        String conversation = open(ana, "bruno");
        String message = send(ana, conversation, "antes do bloqueio");
        bruno.post(conversationPath(conversation) + "/accept", null);
        assertThat(bruno.put("/api/v1/blocks/" + anaId, null)).hasStatus(204);

        assertThat(ana.post(messages(conversation), Map.of("clientMessageId", UUID.randomUUID(), "body", "oi")))
                .hasStatus(403);
        assertThat(bruno.post(messages(conversation), Map.of("clientMessageId", UUID.randomUUID(), "body", "oi")))
                .hasStatus(403);
        JsonNode seenByBruno = bruno.json(bruno.get(conversationPath(conversation)));
        assertThat(seenByBruno.get("blockedByMe").asBoolean()).isTrue();
        assertThat(seenByBruno.get("canSend").asBoolean()).isFalse();

        ApiClient carla = clients.create();
        Accounts.register(carla, "carla", Accounts.invite(ana));
        assertThat(carla.get(conversationPath(conversation))).hasStatus(404);
        assertThat(carla.get(messages(conversation) + "?afterSeq=0")).hasStatus(404);
        assertThat(carla.put("/api/v1/messages/" + message + "/reaction", Map.of("emoji", "👀"))).hasStatus(404);
        assertThat(carla.post("/api/v1/messages/forward", Map.of("messageIds", List.of(message),
                "conversationIds", List.of(conversation)))).hasStatus(404);
        assertThat(ana.post("/api/v1/conversations/direct", Map.of("username", "ana"))).hasStatus(400);
        assertThat(brunoId).isNotBlank();
    }

    private String open(ApiClient client, String username) {
        return open(client, username, false);
    }

    /** Abre a conversa; com {@code create}, cadastra a pessoa antes (convidada pela Ana). */
    private String open(ApiClient client, String username, boolean create) {
        if (create) {
            Accounts.register(clients.create(), username, Accounts.invite(ana));
        }
        var result = client.post("/api/v1/conversations/direct", Map.of("username", username));
        assertThat(result).hasStatusOk();
        return client.json(result).get("id").asString();
    }

    private static String send(ApiClient client, String conversation, String body) {
        Map<String, Object> request = new HashMap<>();
        request.put("clientMessageId", UUID.randomUUID());
        request.put("body", body);
        var result = client.post(messages(conversation), request);
        assertThat(result).hasStatus(201);
        return client.json(result).get("id").asString();
    }

    private static String conversationPath(String conversation) {
        return "/api/v1/conversations/" + conversation;
    }

    private static String messages(String conversation) {
        return conversationPath(conversation) + "/messages";
    }

    private static List<Long> seqs(JsonNode messages) {
        return StreamSupport.stream(messages.spliterator(), false).map(n -> n.get("seq").asLong()).toList();
    }
}
