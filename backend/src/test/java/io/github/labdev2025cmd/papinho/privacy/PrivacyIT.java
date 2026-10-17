package io.github.labdev2025cmd.papinho.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import io.github.labdev2025cmd.papinho.support.Accounts;
import io.github.labdev2025cmd.papinho.support.ApiClient;
import io.github.labdev2025cmd.papinho.support.Images;
import io.github.labdev2025cmd.papinho.support.IntegrationTest;
import io.github.labdev2025cmd.papinho.support.TestSupportConfiguration.ApiClients;
import tools.jackson.databind.JsonNode;

/** Regras de privacidade aplicadas no servidor (PV-02..PV-07, SG-04). */
@IntegrationTest
class PrivacyIT {

    @Autowired
    private ApiClients clients;

    @Autowired
    private JdbcTemplate jdbc;

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
    }

    @Test
    @DisplayName("padrões do WhatsApp: tudo visível, leitura ligada, notificação com conteúdo, sem desfoque")
    void shouldStartWithWhatsAppDefaults() {
        JsonNode settings = ana.json(ana.get("/api/v1/privacy")).get("settings");
        for (String item : List.of("lastSeen", "online", "photo", "about", "groupsAdd")) {
            assertThat(settings.get(item).asString()).as(item).isEqualTo("EVERYONE");
        }
        assertThat(settings.get("readReceipts").asBoolean()).isTrue();
        assertThat(settings.get("hideNotificationContent").asBoolean()).isFalse();
        assertThat(settings.get("blur").get("privateMode").asBoolean()).isFalse();
        JsonNode lock = ana.json(ana.get("/api/v1/privacy/screen-lock"));
        assertThat(lock.get("enabled").asBoolean()).as("bloqueio de tela desligado por padrão").isFalse();

        JsonNode changed = ana.json(ana.patch("/api/v1/privacy", Map.of("hideNotificationContent", true,
                "privateMode", true, "blurNames", true, "revealDelayMs", 1500))).get("settings");
        assertThat(changed.get("hideNotificationContent").asBoolean()).isTrue();
        assertThat(changed.get("blur").get("names").asBoolean()).isTrue();
        assertThat(changed.get("blur").get("messages").asBoolean()).as("o resto fica como estava").isTrue();
        assertThat(changed.get("lastSeen").asString()).isEqualTo("EVERYONE");
        assertThat(ana.patch("/api/v1/privacy", Map.of("revealDelayMs", 60_000))).hasStatus(400);
        assertThat(ana.patch("/api/v1/privacy", Map.of("lastSeen", "AMIGOS"))).hasStatus(400);
    }

    @Test
    @DisplayName("foto e recado: todos, meus contatos, meus contatos exceto e ninguém (PV-04)")
    void shouldApplyPhotoAndAboutVisibility() {
        ana.put("/api/v1/profile", Map.of("displayName", "Ana", "about", "Disponível"));
        ana.multipart(HttpMethod.PUT, "/api/v1/profile/photo",
                new MockMultipartFile("photo", "foto.jpg", "image/jpeg", Images.jpegWithExif(400, 400)));
        assertThat(ana.post("/api/v1/contacts", Map.of("username", "bruno"))).hasStatus(201);
        assertThat(ana.post("/api/v1/contacts", Map.of("username", "carla"))).hasStatus(201);
        assertThat(carla.get(photo(anaId))).hasStatusOk();

        ana.patch("/api/v1/privacy", Map.of("photo", "CONTACTS_EXCEPT", "about", "NOBODY"));
        assertThat(ana.put("/api/v1/privacy/exceptions/PHOTO", Map.of("userIds", List.of(carlaId)))).hasStatusOk();
        assertThat(bruno.get(photo(anaId))).as("contato vê").hasStatusOk();
        assertThat(carla.get(photo(anaId))).as("contato na lista de exceção não vê").hasStatus(404);
        JsonNode card = carla.json(carla.get("/api/v1/people/ana"));
        assertThat(card.get("hasPhoto").asBoolean()).isFalse();
        assertThat(card.get("about").isNull()).as("recado para ninguém").isTrue();
        assertThat(ana.json(ana.get("/api/v1/profile")).get("about").asString()).as("o dono sempre vê")
                .isEqualTo("Disponível");

        ana.patch("/api/v1/privacy", Map.of("photo", "CONTACTS"));
        assertThat(carla.get(photo(anaId))).as("sem exceção, contato vê").hasStatusOk();
        ApiClient dani = clients.create();
        Accounts.register(dani, "dani", Accounts.invite(ana));
        assertThat(dani.get(photo(anaId))).as("quem não é contato não vê").hasStatus(404);

        UUID stranger = UUID.fromString(Accounts.userId(Accounts.register(clients.create(), "edu",
                Accounts.invite(ana))));
        assertThat(ana.put("/api/v1/privacy/exceptions/PHOTO", Map.of("userIds", List.of(stranger))))
                .as("exceção só entre contatos").hasStatus(400);
    }

    @Test
    @DisplayName("visto por último e online com reciprocidade: quem não compartilha não vê o dos outros (PV-04, PV-05)")
    void shouldApplyPresenceReciprocity() {
        String conversation = acceptedConversation();
        jdbc.update("insert into user_presence (user_id, last_seen_at) values (?, ?), (?, ?)", UUID.fromString(anaId),
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5), UUID.fromString(brunoId),
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(3));
        assertThat(conversation).isNotBlank();
        assertThat(lastSeen(ana, brunoId)).as("padrão: visível").isNotNull();
        assertThat(lastSeen(bruno, anaId)).isNotNull();

        bruno.patch("/api/v1/privacy", Map.of("lastSeen", "NOBODY"));
        assertThat(lastSeen(ana, brunoId)).as("Bruno esconde o dele").isNull();
        assertThat(lastSeen(bruno, anaId)).as("reciprocidade: Bruno também não vê o da Ana").isNull();
        assertThat(lastSeen(bruno, brunoId)).as("o próprio sempre vê").isNotNull();

        bruno.patch("/api/v1/privacy", Map.of("lastSeen", "EVERYONE"));
        ana.patch("/api/v1/privacy", Map.of("lastSeen", "CONTACTS"));
        assertThat(lastSeen(bruno, anaId)).as("Bruno não está nos contatos da Ana").isNull();
        assertThat(lastSeen(ana, brunoId)).as("e por isso Ana não vê o dele").isNull();
        ana.post("/api/v1/contacts", Map.of("username", "bruno"));
        assertThat(lastSeen(bruno, anaId)).isNotNull();
        assertThat(lastSeen(ana, brunoId)).isNotNull();

        bruno.patch("/api/v1/privacy", Map.of("lastSeen", "CONTACTS_EXCEPT"));
        bruno.put("/api/v1/privacy/exceptions/LAST_SEEN", Map.of("userIds", List.of(anaId)));
        assertThat(lastSeen(ana, brunoId)).as("Ana está na exceção").isNull();
        assertThat(lastSeen(bruno, anaId)).isNull();

        ana.patch("/api/v1/privacy", Map.of("online", "NOBODY"));
        JsonNode presence = ana.json(ana.get("/api/v1/presence/" + brunoId));
        assertThat(presence.get("online").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("confirmação de leitura: global, por conversa e por grupo, com reciprocidade (PV-05, PV-06)")
    void shouldApplyReadReceiptRules() {
        String conversation = acceptedConversation();
        String message = send(ana, conversation, "oi");
        bruno.put(path(conversation) + "/read", Map.of("seq", 1));
        assertThat(ana.json(ana.get(path(conversation))).get("peerReadSeq").asLong()).isEqualTo(1);

        bruno.patch("/api/v1/privacy", Map.of("readReceipts", false));
        JsonNode anaView = ana.json(ana.get(path(conversation)));
        assertThat(anaView.get("peerReadSeq").isNull()).as("Bruno desligou: Ana não vê a leitura dele").isTrue();
        assertThat(anaView.get("peerDeliveredSeq").asLong()).as("entrega continua visível").isEqualTo(1);
        assertThat(info(ana, message).get("readAt").isNull()).isTrue();
        send(bruno, conversation, "resposta");
        ana.put(path(conversation) + "/read", Map.of("seq", 2));
        JsonNode brunoView = bruno.json(bruno.get(path(conversation)));
        assertThat(brunoView.get("peerReadSeq").isNull()).as("reciprocidade: Bruno não vê a da Ana").isTrue();
        assertThat(brunoView.get("readReceiptsEnabled").asBoolean()).isFalse();

        assertThat(bruno.put(path(conversation) + "/read-receipts", Map.of("enabled", true))).hasStatusOk();
        assertThat(bruno.json(bruno.get(path(conversation))).get("peerReadSeq").asLong())
                .as("sobreposição por conversa liga de novo").isEqualTo(2);
        assertThat(ana.json(ana.get(path(conversation))).get("peerReadSeq").asLong())
                .as("quem envia leu até a própria mensagem").isEqualTo(2);
        Map<String, Object> reset = new HashMap<>();
        reset.put("enabled", null);
        assertThat(bruno.json(bruno.put(path(conversation) + "/read-receipts", reset)).get("readReceipts").isNull())
                .isTrue();
        assertThat(carla.put(path(conversation) + "/read-receipts", Map.of("enabled", false))).as("IDOR")
                .hasStatus(404);

        // Grupo: a confirmação vale mesmo com o global desligado; desligar no grupo esconde nos dois sentidos.
        ana.post("/api/v1/contacts", Map.of("username", "bruno"));
        ana.post("/api/v1/contacts", Map.of("username", "carla"));
        String group = ana.json(ana.post("/api/v1/groups", Map.of("name", "Turma", "memberIds",
                List.of(brunoId, carlaId)))).get("id").asString();
        long groupSeq = ana.json(ana.post(path(group) + "/messages", Map.of("clientMessageId", UUID.randomUUID(),
                "body", "olá grupo"))).get("seq").asLong();
        bruno.put(path(group) + "/read", Map.of("seq", groupSeq));
        carla.put(path(group) + "/read", Map.of("seq", groupSeq));
        assertThat(ana.json(ana.get(path(group))).get("peerReadSeq").asLong()).as("no grupo é sempre enviada")
                .isEqualTo(groupSeq);
        bruno.put(path(group) + "/read-receipts", Map.of("enabled", false));
        assertThat(bruno.json(bruno.get(path(group))).get("peerReadSeq").isNull()).isTrue();
        assertThat(ana.json(ana.get(path(group))).get("peerReadSeq").asLong()).as("Bruno fica de fora do 'todos'")
                .isEqualTo(groupSeq);
        String groupMessage = ana.json(ana.get(path(group) + "/messages")).get("messages").get(2).get("id")
                .asString();
        JsonNode recipients = ana.json(ana.get("/api/v1/messages/" + groupMessage + "/info")).get("recipients");
        for (JsonNode recipient : recipients) {
            boolean isBruno = recipient.get("username").asString().equals("bruno");
            assertThat(recipient.get("readAt").isNull()).as(recipient.get("username").asString()).isEqualTo(isBruno);
        }
    }

    @Test
    @DisplayName("quem pode me adicionar a grupos (PV-07)")
    void shouldRespectWhoCanAddToGroups() {
        ana.post("/api/v1/contacts", Map.of("username", "bruno"));
        ana.post("/api/v1/contacts", Map.of("username", "carla"));
        bruno.patch("/api/v1/privacy", Map.of("groupsAdd", "CONTACTS"));
        MvcTestResult refused = ana.post("/api/v1/groups", Map.of("name", "Turma", "memberIds", List.of(brunoId)));
        assertThat(refused).hasStatus(403);
        assertThat(ana.json(refused).get("type").asString()).isEqualTo("urn:papinho:problem:group-add-not-allowed");

        String group = ana.json(ana.post("/api/v1/groups", Map.of("name", "Turma", "memberIds", List.of(carlaId))))
                .get("id").asString();
        assertThat(ana.post("/api/v1/groups/" + group + "/members", Map.of("userIds", List.of(brunoId))))
                .hasStatus(403);
        bruno.post("/api/v1/contacts", Map.of("username", "ana"));
        assertThat(ana.post("/api/v1/groups/" + group + "/members", Map.of("userIds", List.of(brunoId))))
                .as("agora Ana é contato do Bruno").hasStatusOk();

        // "Meus contatos exceto…": a exceção não adiciona; os outros contatos adicionam.
        String anaId = ana.json(ana.get("/api/v1/me")).get("account").get("id").asString();
        assertThat(bruno.post("/api/v1/contacts", Map.of("username", "carla"))).hasStatus(201);
        bruno.patch("/api/v1/privacy", Map.of("groupsAdd", "CONTACTS_EXCEPT"));
        assertThat(bruno.put("/api/v1/privacy/exceptions/GROUPS_ADD", Map.of("userIds", List.of(anaId))))
                .hasStatusOk();
        assertThat(ana.post("/api/v1/groups", Map.of("name", "Exceção", "memberIds", List.of(brunoId))))
                .as("Ana está na exceção").hasStatus(403);
        carla.post("/api/v1/contacts", Map.of("username", "bruno"));
        assertThat(carla.post("/api/v1/groups", Map.of("name", "Da Carla", "memberIds", List.of(brunoId))))
                .as("Carla é contato e não está na exceção").hasStatus(201);
        assertThat(bruno.put("/api/v1/privacy/exceptions/GROUPS_ADD", Map.of("userIds", List.of()))).hasStatusOk();
        assertThat(ana.post("/api/v1/groups", Map.of("name", "Sem exceção", "memberIds", List.of(brunoId))))
                .as("tirada da exceção").hasStatus(201);

        carla.patch("/api/v1/privacy", Map.of("groupsAdd", "NOBODY"));
        String code = ana.json(ana.get("/api/v1/groups/" + group + "/invite")).get("code").asString();
        assertThat(ana.post("/api/v1/groups", Map.of("name", "Outra", "memberIds", List.of(carlaId))))
                .hasStatus(403);
        assertThat(code).as("quem não aceita ser adicionado entra pelo link").isNotBlank();
    }

    @Test
    @DisplayName("bloqueio de tela: PIN próprio com Argon2, troca exige o atual, tentativas limitadas (PV-02)")
    void shouldManageScreenLock() {
        assertThat(ana.post("/api/v1/privacy/screen-lock/unlock", Map.of("secret", "1234"))).hasStatus(404);
        assertThat(ana.put("/api/v1/privacy/screen-lock", Map.of("kind", "PIN", "secret", "12a4",
                "lockOnHidden", false, "allowPasskey", false))).as("PIN só com dígitos").hasStatus(400);
        JsonNode created = ana.json(ana.put("/api/v1/privacy/screen-lock", Map.of("kind", "PIN", "secret", "2468",
                "idleMinutes", 5, "lockOnHidden", true, "shortcut", "Alt+Shift+L", "allowPasskey", true)));
        assertThat(created.get("enabled").asBoolean()).isTrue();
        assertThat(created.get("idleMinutes").asInt()).isEqualTo(5);
        assertThat(created.has("secretHash")).as("hash nunca vai ao cliente").isFalse();
        String hash = jdbc.queryForObject("select secret_hash from screen_locks", String.class);
        assertThat(hash).startsWith("$argon2").doesNotContain("2468");

        assertThat(ana.post("/api/v1/privacy/screen-lock/unlock", Map.of("secret", "2468"))).hasStatus(204);
        MvcTestResult wrong = ana.post("/api/v1/privacy/screen-lock/unlock", Map.of("secret", "0000"));
        assertThat(wrong).hasStatus(403);
        assertThat(ana.json(wrong).get("type").asString()).isEqualTo("urn:papinho:problem:screen-lock-invalid");
        assertThat(ana.put("/api/v1/privacy/screen-lock", Map.of("kind", "PIN", "secret", "1357",
                "lockOnHidden", false, "allowPasskey", false))).as("trocar o PIN sem o atual").hasStatus(403);
        assertThat(ana.put("/api/v1/privacy/screen-lock", Map.of("lockOnHidden", false, "allowPasskey", false,
                "shortcut", "L"))).as("atalho sem modificador").hasStatus(400);
        JsonNode triggers = ana.json(ana.put("/api/v1/privacy/screen-lock", Map.of("lockOnHidden", false,
                "allowPasskey", false)));
        assertThat(triggers.get("idleMinutes").isNull()).as("gatilhos mudam sem o segredo").isTrue();
        assertThat(bruno.json(bruno.get("/api/v1/privacy/screen-lock")).get("enabled").asBoolean())
                .as("cada um tem o seu").isFalse();

        int status = 0;
        for (int attempt = 0; attempt < 15 && status != 429; attempt++) {
            status = ana.post("/api/v1/privacy/screen-lock/unlock", Map.of("secret", "9999")).getResponse()
                    .getStatus();
        }
        assertThat(status).as("força bruta limitada").isEqualTo(429);
        assertThat(bruno.post("/api/v1/privacy/screen-lock/unlock", Map.of("secret", "9999"))).hasStatus(404);
    }

    @Test
    @DisplayName("conversa trancada: some da lista, conteúdo só com token, código compartilhado, IDOR (PV-03)")
    void shouldLockConversations() throws Exception {
        String conversation = acceptedConversation();
        send(ana, conversation, "segredo");
        ana.post("/api/v1/contacts", Map.of("username", "carla"));
        String other = ana.json(ana.post("/api/v1/conversations/direct", Map.of("username", "carla"))).get("id")
                .asString();
        send(ana, other, "outro segredo");

        assertThat(carla.put("/api/v1/privacy/locked-conversations/" + conversation, Map.of("code", "4321")))
                .as("IDOR").hasStatus(404);
        assertThat(ana.put("/api/v1/privacy/locked-conversations/" + conversation, Map.of("code", "12")))
                .as("código curto").hasStatus(400);
        String token = ana.json(ana.put("/api/v1/privacy/locked-conversations/" + conversation,
                Map.of("code", "4321"))).get("token").asString();
        ana.put("/api/v1/privacy/locked-conversations/" + other, Map.of("code", "4321"));
        assertThat(jdbc.queryForObject("select count(*) from lock_codes", Integer.class))
                .as("o mesmo código vira código compartilhado").isEqualTo(1);

        JsonNode list = ana.json(ana.get("/api/v1/conversations"));
        assertThat(list).as("trancadas saem da lista").isEmpty();
        assertThat(ana.json(ana.get("/api/v1/privacy/locked-conversations")).get("count").asInt()).isEqualTo(2);
        assertThat(ana.get(path(conversation))).hasStatus(403);
        assertThat(ana.get(path(conversation) + "/messages")).hasStatus(403);
        assertThat(ana.get(path(conversation) + "/messages?afterSeq=0")).hasStatus(403);
        assertThat(ana.get(path(conversation) + "/gallery?tab=MEDIA")).hasStatus(403);
        assertThat(ana.json(ana.get("/api/v1/conversations?locked=true"))).as("pasta sem token").isEmpty();
        assertThat(bruno.json(bruno.get("/api/v1/conversations"))).as("só tranca para quem trancou").hasSize(1);
        assertThat(bruno.get(path(conversation) + "/messages")).hasStatusOk();

        assertThat(withToken(ana, "/api/v1/conversations/" + conversation + "/messages", token)).hasStatusOk();
        assertThat(withToken(ana, "/api/v1/conversations/" + conversation + "/messages", "inventado"))
                .hasStatus(403);
        assertThat(withToken(bruno, "/api/v1/conversations?locked=true", token).getResponse()
                .getContentAsString()).as("token é da Ana").isEqualTo("[]");

        assertThat(ana.post("/api/v1/privacy/locked-conversations/unlock", Map.of("code", "0000"))).hasStatus(403);
        String folder = ana.json(ana.post("/api/v1/privacy/locked-conversations/unlock", Map.of("code", "4321")))
                .get("token").asString();
        JsonNode unlocked = ana.json(withToken(ana, "/api/v1/conversations?locked=true", folder));
        assertThat(unlocked).hasSize(2);
        assertThat(unlocked.get(0).get("locked").asBoolean()).isTrue();

        assertThat(ana.delete("/api/v1/privacy/locked-conversations/" + other, null))
                .as("destrancar de vez exige o token").hasStatus(403);
        assertThat(ana.perform(MockMvcRequestBuilders.delete("/api/v1/privacy/locked-conversations/" + other)
                .header(ConversationLocks.HEADER, folder))).hasStatus(204);
        assertThat(ana.json(ana.get("/api/v1/conversations"))).as("voltou para a lista").hasSize(1);

        assertThat(ana.perform(MockMvcRequestBuilders.post("/api/v1/privacy/locked-conversations/lock-all")
                .header(ConversationLocks.HEADER, folder))).hasStatus(204);
        assertThat(withToken(ana, "/api/v1/conversations?locked=true", folder).getResponse().getContentAsString())
                .as("fechar a pasta invalida o token").isEqualTo("[]");

        int status = 0;
        for (int attempt = 0; attempt < 15 && status != 429; attempt++) {
            status = ana.post("/api/v1/privacy/locked-conversations/unlock", Map.of("code", "9999")).getResponse()
                    .getStatus();
        }
        assertThat(status).as("força bruta limitada").isEqualTo(429);
    }

    /** Ana escreve para Bruno, que tem Ana nos contatos: conversa aceita pelos dois lados. */
    @Test
    @DisplayName("quem desligou a própria confirmação não vê a leitura em Dados da mensagem; no grupo, o 'lida por todos' ignora quem desligou (PV-05, PV-06)")
    void shouldApplyReadReceiptRulesToInfoAndGroupList() {
        String conversation = acceptedConversation();
        String message = send(ana, conversation, "oi");
        ana.patch("/api/v1/privacy", Map.of("readReceipts", false));
        bruno.put(path(conversation) + "/read", Map.of("seq", 1));
        assertThat(info(ana, message).get("readAt").isNull()).as("Ana desligou a dela: não vê a do Bruno").isTrue();
        ana.patch("/api/v1/privacy", Map.of("readReceipts", true));
        assertThat(info(ana, message).get("readAt").isNull()).as("religada, volta a ver").isFalse();

        ana.post("/api/v1/contacts", Map.of("username", "bruno"));
        ana.post("/api/v1/contacts", Map.of("username", "carla"));
        String group = ana.json(ana.post("/api/v1/groups", Map.of("name", "Turma", "memberIds",
                List.of(brunoId, carlaId)))).get("id").asString();
        bruno.put(path(group) + "/read-receipts", Map.of("enabled", false));
        // O global desligado não vale no grupo: lá a confirmação é sempre ligada, salvo na própria conversa.
        carla.patch("/api/v1/privacy", Map.of("readReceipts", false));
        JsonNode sent = ana.json(ana.post(path(group) + "/messages", Map.of("clientMessageId", UUID.randomUUID(),
                "body", "olá grupo")));
        long seq = sent.get("seq").asLong();
        carla.put(path(group) + "/read", Map.of("seq", seq));
        assertThat(ana.json(ana.get(path(group))).get("peerReadSeq").asLong())
                .as("Bruno desligou e não leu: fica fora do 'lida por todos'").isEqualTo(seq);
        assertThat(java.util.stream.StreamSupport.stream(ana.json(ana.get("/api/v1/messages/"
                + sent.get("id").asString() + "/info")).get("recipients").spliterator(), false)
                .filter(r -> r.get("userId").asString().equals(carlaId)).findFirst().orElseThrow()
                .get("readAt").isNull()).as("leitura da Carla no grupo, com o global dela desligado").isFalse();
        JsonNode listed = java.util.stream.StreamSupport.stream(ana.json(ana.get("/api/v1/conversations"))
                .spliterator(), false).filter(item -> item.get("id").asString().equals(group)).findFirst().orElseThrow();
        assertThat(listed.get("peerReadSeq").asLong()).as("na lista de conversas").isEqualTo(seq);
    }

    @Test
    @DisplayName("conversa trancada: responder citando uma mensagem exige o token, senão a citação vazaria o texto (PV-03)")
    void shouldRequireTokenToQuoteInLockedConversation() throws Exception {
        String conversation = acceptedConversation();
        String secret = send(bruno, conversation, "respostasecreta");
        String token = ana.json(ana.put("/api/v1/privacy/locked-conversations/" + conversation,
                Map.of("code", "4321"))).get("token").asString();

        MvcTestResult quoted = ana.post(path(conversation) + "/messages",
                Map.of("clientMessageId", UUID.randomUUID(), "body", "oi", "replyToId", secret));
        assertThat(quoted).as("citar sem o token").hasStatus(403);
        assertThat(ApiClient.text(quoted)).doesNotContain("respostasecreta");

        MvcTestResult allowed = ana.perform(MockMvcRequestBuilders.post(path(conversation) + "/messages")
                .header(ConversationLocks.HEADER, token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientMessageId\":\"" + UUID.randomUUID() + "\",\"body\":\"oi\",\"replyToId\":\"" + secret
                        + "\"}"));
        assertThat(allowed).as("com o token").hasStatus(201);
    }

    @Test
    @DisplayName("conversa trancada: listar e agendar mensagens exige o token (PV-03)")
    void shouldRequireTokenForScheduledInLockedConversation() throws Exception {
        String conversation = acceptedConversation();
        String sendAt = java.time.Instant.now().plusSeconds(3600).toString();
        assertThat(ana.post(path(conversation) + "/scheduled", Map.of("body", "agendadasecreta", "sendAt", sendAt)))
                .hasStatus(201);
        String token = ana.json(ana.put("/api/v1/privacy/locked-conversations/" + conversation,
                Map.of("code", "4321"))).get("token").asString();

        MvcTestResult listed = ana.get(path(conversation) + "/scheduled");
        assertThat(listed).as("listar sem o token").hasStatus(403);
        assertThat(ApiClient.text(listed)).doesNotContain("agendadasecreta");
        assertThat(ana.post(path(conversation) + "/scheduled", Map.of("body", "outra", "sendAt", sendAt)))
                .as("agendar sem o token").hasStatus(403);
        assertThat(withToken(ana, path(conversation) + "/scheduled", token).getResponse().getContentAsString())
                .as("com o token").contains("agendadasecreta");
    }

    @Test
    @DisplayName("conversa trancada: o anexo só abre com o grant que vem na mensagem destrancada (PV-03)")
    void shouldRequireGrantForLockedAttachments() throws Exception {
        String conversation = acceptedConversation();
        String photo = io.github.labdev2025cmd.papinho.support.Uploads.upload(bruno, Images.png(32, 32),
                "trancada.png", "image/png");
        io.github.labdev2025cmd.papinho.support.Uploads.send(bruno, conversation, photo, "");
        String own = io.github.labdev2025cmd.papinho.support.Uploads.upload(ana, Images.png(32, 32), "minha.png",
                "image/png");
        io.github.labdev2025cmd.papinho.support.Uploads.send(ana, conversation, own, "");
        String token = ana.json(ana.put("/api/v1/privacy/locked-conversations/" + conversation,
                Map.of("code", "4321"))).get("token").asString();

        assertThat(ana.get("/api/v1/media/" + photo + "/content")).as("pelo id, sem grant").hasStatus(404);
        assertThat(ana.get("/api/v1/media/" + photo + "/thumbnail")).hasStatus(404);
        assertThat(ana.get("/api/v1/media/" + own + "/content")).as("nem o próprio arquivo").hasStatus(404);
        assertThat(bruno.get("/api/v1/media/" + photo + "/content")).as("para o Bruno não está trancada")
                .hasStatusOk();

        JsonNode page = ana.json(withToken(ana, path(conversation) + "/messages", token)).get("messages");
        Map<String, String> grants = new HashMap<>();
        page.forEach(m -> {
            if (m.hasNonNull("attachment")) {
                grants.put(m.get("attachment").get("id").asString(), m.get("attachment").get("grant").asString());
            }
        });
        assertThat(grants).containsKeys(photo, own);
        assertThat(ana.get("/api/v1/media/" + photo + "/content?grant=" + grants.get(photo))).hasStatusOk();
        assertThat(ana.get("/api/v1/media/" + photo + "/thumbnail?grant=" + grants.get(photo))).hasStatusOk();
        assertThat(ana.get("/api/v1/media/" + own + "/content?grant=" + grants.get(own))).hasStatusOk();
        assertThat(ana.get("/api/v1/media/" + photo + "/content?grant=" + grants.get(own)))
                .as("grant de outro arquivo").hasStatus(404);
        assertThat(bruno.get("/api/v1/media/" + own + "/content?grant=" + grants.get(own)))
                .as("o grant é da Ana, mas o Bruno não precisa").hasStatusOk();
        assertThat(ana.get("/api/v1/media/" + photo + "/content?grant=1.abc")).hasStatus(404);
        assertThat(bruno.json(bruno.get(path(conversation) + "/messages")).get("messages").get(0).get("attachment")
                .get("grant").isNull()).as("quem não trancou não recebe grant").isTrue();
    }

    @Test
    @DisplayName("conversa trancada: a favorita dela some da lista de favoritas sem o token (PV-03)")
    void shouldHideStarredOfLockedConversation() throws Exception {
        String conversation = acceptedConversation();
        String secret = send(bruno, conversation, "favoritasecreta");
        assertThat(ana.put("/api/v1/messages/" + secret + "/star", null)).hasStatusOk();
        assertThat(ApiClient.text(ana.get("/api/v1/messages/starred"))).contains("favoritasecreta");
        String token = ana.json(ana.put("/api/v1/privacy/locked-conversations/" + conversation,
                Map.of("code", "4321"))).get("token").asString();

        MvcTestResult hidden = ana.get("/api/v1/messages/starred");
        assertThat(hidden).hasStatusOk();
        assertThat(ApiClient.text(hidden)).as("sem o token").doesNotContain("favoritasecreta");
        assertThat(withToken(ana, "/api/v1/messages/starred", token).getResponse().getContentAsString())
                .as("com o token").contains("favoritasecreta");
    }

    private String acceptedConversation() {
        bruno.post("/api/v1/contacts", Map.of("username", "ana"));
        return ana.json(ana.post("/api/v1/conversations/direct", Map.of("username", "bruno"))).get("id").asString();
    }

    private static String send(ApiClient client, String conversation, String body) {
        MvcTestResult result = client.post(path(conversation) + "/messages",
                Map.of("clientMessageId", UUID.randomUUID(), "body", body));
        assertThat(result).as(ApiClient.text(result)).hasStatus(201);
        return client.json(result).get("id").asString();
    }

    private static JsonNode info(ApiClient client, String messageId) {
        return client.json(client.get("/api/v1/messages/" + messageId + "/info")).get("recipients").get(0);
    }

    private static @Nullable String lastSeen(ApiClient viewer, String userId) {
        JsonNode node = viewer.json(viewer.get("/api/v1/presence/" + userId)).get("lastSeenAt");
        return node == null || node.isNull() ? null : node.asString();
    }

    private static MvcTestResult withToken(ApiClient client, String uri, String token) {
        return client.perform(MockMvcRequestBuilders.get(uri).header(ConversationLocks.HEADER, token)
                .accept(MediaType.APPLICATION_JSON));
    }

    private static String path(String conversation) {
        return "/api/v1/conversations/" + conversation;
    }

    private static String photo(String userId) {
        return "/api/v1/users/" + userId + "/photo";
    }
}
