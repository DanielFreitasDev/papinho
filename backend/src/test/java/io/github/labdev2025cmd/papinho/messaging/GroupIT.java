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
import io.github.labdev2025cmd.papinho.support.Images;
import io.github.labdev2025cmd.papinho.support.IntegrationTest;
import io.github.labdev2025cmd.papinho.support.TestSupportConfiguration.ApiClients;
import io.github.labdev2025cmd.papinho.support.Uploads;
import tools.jackson.databind.JsonNode;

/** Grupos (GR-01), convite por link (GR-02), mensagens salvas (MS-12) e responder em privado. */
@IntegrationTest
class GroupIT {

    @Autowired
    private ApiClients clients;

    private ApiClient ana;
    private ApiClient bruno;
    private ApiClient carla;
    private String anaId;
    private String brunoId;
    private String carlaId;

    @BeforeEach
    void setUp() {
        ana = clients.create();
        anaId = Accounts.userId(Accounts.register(ana, "ana", null));
        bruno = clients.create();
        brunoId = Accounts.userId(Accounts.register(bruno, "bruno", Accounts.invite(ana)));
        carla = clients.create();
        carlaId = Accounts.userId(Accounts.register(carla, "carla", Accounts.invite(ana)));
        assertThat(ana.post("/api/v1/contacts", Map.of("username", "bruno"))).hasStatus(201);
        assertThat(ana.post("/api/v1/contacts", Map.of("username", "carla"))).hasStatus(201);
    }

    @Test
    @DisplayName("criar grupo com contatos: avisos do sistema, lista, mensagens e leitura de todos os membros")
    void shouldCreateGroupAndExchangeMessages() {
        ApiClient dani = clients.create();
        String daniId = Accounts.userId(Accounts.register(dani, "dani", Accounts.invite(ana)));
        assertThat(ana.post("/api/v1/groups", Map.of("name", "Família", "memberIds", List.of(daniId))))
                .as("só contatos").hasStatus(403);

        JsonNode group = ana.json(ana.post("/api/v1/groups",
                Map.of("name", "  Família ", "description", "Só nós", "memberIds", List.of(brunoId, carlaId))));
        String id = group.get("id").asString();
        assertThat(group.get("kind").asString()).isEqualTo("GROUP");
        assertThat(group.get("peer").isNull()).isTrue();
        assertThat(group.get("group").get("name").asString()).isEqualTo("Família");
        assertThat(group.get("group").get("memberCount").asLong()).isEqualTo(3);
        assertThat(group.get("group").get("myRole").asString()).isEqualTo("ADMIN");

        JsonNode brunoList = bruno.json(bruno.get("/api/v1/conversations"));
        assertThat(brunoList).hasSize(1);
        assertThat(brunoList.get(0).get("group").get("myRole").asString()).isEqualTo("MEMBER");
        assertThat(brunoList.get(0).get("unreadCount").asLong()).as("aviso não conta como não lida").isZero();
        JsonNode notices = bruno.json(bruno.get(messages(id) + "?afterSeq=0")).get("messages");
        assertThat(notices).hasSize(2);
        assertThat(notices.get(0).get("kind").asString()).isEqualTo("SYSTEM");
        assertThat(notices.get(0).get("body").asString()).contains("\"type\":\"group.created\"")
                .contains("Família");
        assertThat(notices.get(1).get("body").asString()).contains("group.added").contains(carlaId);

        String hello = send(ana, id, "Oi, família");
        assertThat(bruno.json(bruno.get(conversation(id))).get("lastMessage").get("senderLabel").asString())
                .isEqualTo("Pessoa ana");
        bruno.put(conversation(id) + "/read", Map.of("seq", 3));
        assertThat(ana.json(ana.get(conversation(id))).get("peerReadSeq").asLong())
                .as("lida só quando todos leram").isLessThan(3);
        carla.put(conversation(id) + "/read", Map.of("seq", 3));
        assertThat(ana.json(ana.get(conversation(id))).get("peerReadSeq").asLong()).isEqualTo(3);
        JsonNode info = ana.json(ana.get("/api/v1/messages/" + hello + "/info"));
        assertThat(info.get("recipients")).hasSize(2);

        assertThat(dani.get(conversation(id))).as("IDOR").hasStatus(404);
        assertThat(dani.get("/api/v1/groups/" + id + "/members")).hasStatus(404);
        assertThat(ana.patch("/api/v1/messages/" + notices.get(0).get("id").asString(), Map.of("body", "x")))
                .as("aviso não se edita").hasStatus(403);
    }

    @Test
    @DisplayName("permissões, admins, remover, sair silenciosamente e o membro mais antigo vira admin")
    void shouldManagePermissionsAndMembers() {
        String id = createGroup();
        assertThat(ana.put("/api/v1/groups/" + id + "/settings", Map.of("onlyAdminsSend", true,
                "onlyAdminsEditInfo", true, "onlyAdminsAddMembers", true, "joinApproval", false))).hasStatusOk();
        JsonNode seenByBruno = bruno.json(bruno.get(conversation(id)));
        assertThat(seenByBruno.get("canSend").asBoolean()).isFalse();
        assertThat(seenByBruno.get("group").get("canEditInfo").asBoolean()).isFalse();
        assertThat(bruno.post(messages(id), Map.of("clientMessageId", UUID.randomUUID(), "body", "oi")))
                .hasStatus(403);
        assertThat(bruno.patch("/api/v1/groups/" + id, Map.of("name", "Outro"))).hasStatus(403);
        assertThat(bruno.put("/api/v1/groups/" + id + "/settings", Map.of("onlyAdminsSend", false,
                "onlyAdminsEditInfo", false, "onlyAdminsAddMembers", false, "joinApproval", false))).hasStatus(403);

        JsonNode members = ana.json(ana.put("/api/v1/groups/" + id + "/admins/" + brunoId, null));
        assertThat(roles(members)).containsEntry(brunoId, "ADMIN");
        assertThat(bruno.post(messages(id), Map.of("clientMessageId", UUID.randomUUID(), "body", "admin fala")))
                .hasStatus(201);
        assertThat(bruno.json(bruno.patch("/api/v1/groups/" + id, Map.of("name", "Família 2"))).get("group")
                .get("name").asString()).isEqualTo("Família 2");

        assertThat(bruno.delete("/api/v1/groups/" + id + "/members/" + carlaId, null)).hasStatus(204);
        assertThat(carla.get(conversation(id))).as("removida não vê mais").hasStatus(404);
        assertThat(carla.json(carla.get("/api/v1/conversations"))).isEmpty();

        long before = ana.json(ana.get(conversation(id))).get("lastSeq").asLong();
        assertThat(ana.post("/api/v1/groups/" + id + "/leave", null)).hasStatus(204);
        assertThat(bruno.json(bruno.get(conversation(id))).get("lastSeq").asLong()).as("sem aviso ao sair")
                .isEqualTo(before);
        assertThat(bruno.post("/api/v1/groups/" + id + "/leave", null)).hasStatus(204);
        assertThat(ana.get(conversation(id))).hasStatus(404);

        String other = createGroup();
        ana.post("/api/v1/groups/" + other + "/leave", null);
        JsonNode remaining = bruno.json(bruno.get("/api/v1/groups/" + other + "/members"));
        assertThat(remaining.get(0).get("role").asString()).as("o mais antigo vira admin").isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("bloqueio impede adicionar (CT-03); foto do grupo só para membros")
    void shouldRespectBlocksAndProtectPhoto() {
        String id = ana.json(ana.post("/api/v1/groups", Map.of("name", "Fotos", "memberIds", List.of(brunoId))))
                .get("id").asString();
        assertThat(carla.put("/api/v1/blocks/" + anaId, null)).hasStatus(204);
        assertThat(ana.post("/api/v1/groups/" + id + "/members", Map.of("userIds", List.of(carlaId))))
                .hasStatus(403);

        String photo = Uploads.upload(ana, Images.png(64, 64), "grupo.png", "image/png");
        JsonNode changed = ana.json(ana.put("/api/v1/groups/" + id + "/photo", Map.of("mediaId", photo)));
        assertThat(changed.get("group").get("photoId").asString()).isEqualTo(photo);
        assertThat(bruno.get("/api/v1/media/" + photo + "/thumbnail")).hasStatusOk();
        assertThat(carla.get("/api/v1/media/" + photo + "/thumbnail")).hasStatus(404);
        Map<String, Object> remove = new HashMap<>();
        remove.put("mediaId", null);
        ana.put("/api/v1/groups/" + id + "/photo", remove);
        assertThat(ana.get("/api/v1/media/" + photo + "/content")).as("foto antiga removida").hasStatus(404);
    }

    @Test
    @DisplayName("convite por link: prévia, entrar, aprovação de entrada, revogar e só admins geram (GR-02)")
    void shouldJoinByInviteLink() {
        String id = ana.json(ana.post("/api/v1/groups", Map.of("name", "Aberto", "memberIds", List.of(brunoId))))
                .get("id").asString();
        assertThat(bruno.get("/api/v1/groups/" + id + "/invite")).as("só admins").hasStatus(403);
        assertThat(carla.get("/api/v1/groups/" + id + "/invite")).hasStatus(404);
        JsonNode invite = ana.json(ana.get("/api/v1/groups/" + id + "/invite"));
        String code = invite.get("code").asString();
        assertThat(invite.get("url").asString()).endsWith("/g/" + code);
        assertThat(invite.get("qrSvg").asString()).startsWith("<svg");

        JsonNode preview = carla.json(carla.get("/api/v1/groups/invites/" + code));
        assertThat(preview.get("name").asString()).isEqualTo("Aberto");
        assertThat(preview.get("memberCount").asLong()).isEqualTo(2);
        assertThat(preview.get("member").asBoolean()).isFalse();
        JsonNode joined = carla.json(carla.post("/api/v1/groups/invites/" + code + "/join", null));
        assertThat(joined.get("pending").asBoolean()).isFalse();
        assertThat(carla.json(carla.get(conversation(id))).get("unreadCount").asLong())
                .as("histórico anterior não conta como não lido").isZero();
        assertThat(ana.json(ana.get("/api/v1/groups/" + id + "/members"))).hasSize(3);

        ana.delete("/api/v1/groups/" + id + "/members/" + carlaId, null);
        ana.put("/api/v1/groups/" + id + "/settings", Map.of("onlyAdminsSend", false, "onlyAdminsEditInfo", false,
                "onlyAdminsAddMembers", false, "joinApproval", true));
        JsonNode pending = carla.json(carla.post("/api/v1/groups/invites/" + code + "/join", null));
        assertThat(pending.get("pending").asBoolean()).isTrue();
        assertThat(carla.get(conversation(id))).as("pendente não vê o grupo").hasStatus(404);
        JsonNode requests = ana.json(ana.get("/api/v1/groups/" + id + "/requests"));
        assertThat(requests.get(0).get("userId").asString()).isEqualTo(carlaId);
        assertThat(bruno.get("/api/v1/groups/" + id + "/requests")).hasStatus(403);
        assertThat(ana.post("/api/v1/groups/" + id + "/requests/" + carlaId + "/approve", null)).hasStatus(204);
        assertThat(carla.get(conversation(id))).hasStatusOk();

        String reset = ana.json(ana.post("/api/v1/groups/" + id + "/invite/reset", null)).get("code").asString();
        assertThat(reset).isNotEqualTo(code);
        assertThat(carla.get("/api/v1/groups/invites/" + code)).as("link antigo").hasStatus(404);
        assertThat(ana.delete("/api/v1/groups/" + id + "/invite", null)).hasStatus(204);
        assertThat(carla.get("/api/v1/groups/invites/" + reset)).hasStatus(404);
    }

    @Test
    @DisplayName("mensagens salvas (MS-12) e responder em privado a partir do grupo")
    void shouldKeepSavedMessagesAndReplyPrivately() {
        JsonNode saved = ana.json(ana.post("/api/v1/conversations/saved", null));
        assertThat(saved.get("kind").asString()).isEqualTo("SELF");
        assertThat(saved.get("peer").get("userId").asString()).isEqualTo(anaId);
        assertThat(ana.json(ana.post("/api/v1/conversations/saved", null)).get("id").asString())
                .isEqualTo(saved.get("id").asString());
        send(ana, saved.get("id").asString(), "lembrar de comprar pão");
        assertThat(ana.json(ana.get("/api/v1/conversations")).get(0).get("kind").asString()).isEqualTo("SELF");
        assertThat(bruno.get(conversation(saved.get("id").asString()))).hasStatus(404);

        String group = createGroup();
        String fromAna = send(ana, group, "quem vem amanhã?");
        assertThat(bruno.post("/api/v1/contacts", Map.of("username", "ana"))).hasStatus(201);
        String direct = bruno.json(bruno.post("/api/v1/conversations/direct", Map.of("username", "ana")))
                .get("id").asString();
        JsonNode reply = bruno.json(bruno.post(messages(direct),
                Map.of("clientMessageId", UUID.randomUUID(), "body", "eu vou", "replyToId", fromAna)));
        assertThat(reply.get("replyTo").get("conversationId").asString()).isEqualTo(group);
        String carlaDirect = carla.json(carla.post("/api/v1/conversations/direct", Map.of("username", "bruno")))
                .get("id").asString();
        assertThat(carla.post(messages(carlaDirect),
                Map.of("clientMessageId", UUID.randomUUID(), "body", "x", "replyToId", fromAna)))
                .as("só cita mensagem de quem está na conversa").hasStatus(400);
    }

    private String createGroup() {
        return ana.json(ana.post("/api/v1/groups", Map.of("name", "Família", "memberIds", List.of(brunoId, carlaId))))
                .get("id").asString();
    }

    private static Map<String, String> roles(JsonNode members) {
        Map<String, String> result = new HashMap<>();
        StreamSupport.stream(members.spliterator(), false)
                .forEach(m -> result.put(m.get("userId").asString(), m.get("role").asString()));
        return result;
    }

    private static String send(ApiClient client, String conversationId, String body) {
        var result = client.post(messages(conversationId), Map.of("clientMessageId", UUID.randomUUID(), "body", body));
        assertThat(result).as(ApiClient.text(result)).hasStatus(201);
        return client.json(result).get("id").asString();
    }

    private static String conversation(String id) {
        return "/api/v1/conversations/" + id;
    }

    private static String messages(String id) {
        return conversation(id) + "/messages";
    }
}
