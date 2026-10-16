package io.github.labdev2025cmd.papinho.backup;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import io.github.labdev2025cmd.papinho.support.Accounts;
import io.github.labdev2025cmd.papinho.support.ApiClient;
import io.github.labdev2025cmd.papinho.support.Images;
import io.github.labdev2025cmd.papinho.support.IntegrationTest;
import io.github.labdev2025cmd.papinho.support.TestSupportConfiguration.ApiClients;
import io.github.labdev2025cmd.papinho.support.Uploads;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Exportar os dados da conta e excluir a conta (BK-02, LGPD). */
@IntegrationTest
class AccountDataIT {

    @Autowired
    private ApiClients clients;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JsonMapper json;

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
        bruno.post("/api/v1/contacts", Map.of("username", "ana", "nickname", "Aninha"));
        ana.post("/api/v1/contacts", Map.of("username", "bruno"));
        conversation = bruno.json(bruno.post("/api/v1/conversations/direct", Map.of("username", "ana")))
                .get("id").asString();
    }

    @Test
    @DisplayName("exporta conta, perfil, contatos, mensagens enviadas e arquivos, sem nenhum segredo")
    void shouldExportPersonalData() throws IOException {
        send(bruno, "minha mensagem", null);
        send(ana, "mensagem da Ana", null);
        String upload = Uploads.upload(bruno, Images.png(16, 16), "nota.png", "image/png");
        send(bruno, "", upload);
        assertThat(bruno.post("/api/v1/folders", Map.of("name", "Família", "conversationIds",
                java.util.List.of(conversation)))).hasStatus(201);

        Map<String, byte[]> zip = unzip(bruno.get("/api/v1/account/export?files=true"));
        String raw = new String(zip.get("dados.json"), StandardCharsets.UTF_8);
        JsonNode data = json.readTree(raw);
        assertThat(data.get("conta").get("usuario").asString()).isEqualTo("bruno");
        assertThat(data.get("perfil").get("nome").asString()).isEqualTo("Pessoa bruno");
        assertThat(data.get("contatos").get("contatos").get(0).get("apelido").asString()).isEqualTo("Aninha");
        assertThat(data.get("mensagens").get("enviadas").toString()).contains("minha mensagem")
                .doesNotContain("mensagem da Ana");
        assertThat(data.get("mensagens").get("listas").get(0).get("nome").asString()).isEqualTo("Família");
        assertThat(data.get("arquivos").get(0).get("nome").asString()).isEqualTo("nota.png");
        assertThat(data.has("privacidade")).isTrue();
        assertThat(data.has("notificacoes")).isTrue();
        assertThat(raw).doesNotContain("password").doesNotContain("argon").doesNotContain("secret_hash")
                .doesNotContain("totp_secret").doesNotContain("p256dh");
        assertThat(zip.keySet()).anyMatch(name -> name.startsWith("arquivos/") && name.endsWith("nota.png"));
        assertThat(unzip(bruno.get("/api/v1/account/export")).keySet()).containsOnly("dados.json");
    }

    @Test
    @DisplayName("excluir exige a senha; o último admin não sai; sessões caem e as mensagens ficam sem dono")
    void shouldDeleteAccount() {
        String groupId = ana.json(ana.post("/api/v1/groups", Map.of("name", "Turma", "memberIds",
                java.util.List.of(brunoId)))).get("id").asString();
        jdbc.update("update conversation_members set role = 'MEMBER' where user_id = (select id from user_accounts "
                + "where username = 'ana') and conversation_id = ?", UUID.fromString(groupId));
        jdbc.update("update conversation_members set role = 'ADMIN' where user_id = ? and conversation_id = ?",
                UUID.fromString(brunoId), UUID.fromString(groupId));
        send(bruno, "fica para a Ana", null);

        assertThat(bruno.delete("/api/v1/account", Map.of("password", "senha errada errada"))).hasStatus(401);
        MvcTestResult lastAdmin = ana.delete("/api/v1/account", Map.of("password", Accounts.PASSWORD));
        assertThat(lastAdmin).hasStatus(409);
        assertThat(ana.json(lastAdmin).get("type").asString()).isEqualTo("urn:papinho:problem:account-last-admin");

        assertThat(bruno.delete("/api/v1/account", Map.of("password", Accounts.PASSWORD))).hasStatus(204);
        assertThat(bruno.get("/api/v1/account/export")).as("sessão encerrada").hasStatus(401);
        assertThat(jdbc.queryForObject("select count(*) from user_accounts where username = 'bruno'", Integer.class))
                .isZero();
        JsonNode history = ana.json(ana.get("/api/v1/conversations/" + conversation + "/messages")).get("messages");
        assertThat(history.get(0).get("body").asString()).isEqualTo("fica para a Ana");
        assertThat(history.get(0).get("senderId").isNull()).as("Conta apagada").isTrue();
        JsonNode kept = ana.json(ana.get("/api/v1/conversations/" + conversation));
        assertThat(kept.get("peer").isNull()).as("a conversa fica, sem a outra pessoa").isTrue();
        assertThat(kept.get("canSend").asBoolean()).isFalse();
        assertThat(ana.post("/api/v1/conversations/" + conversation + "/messages",
                Map.of("clientMessageId", UUID.randomUUID(), "body", "alô?"))).hasStatus(403);
        assertThat(jdbc.queryForObject("select role from conversation_members where conversation_id = ?",
                String.class, UUID.fromString(groupId))).as("o admin do grupo passa para quem ficou")
                .isEqualTo("ADMIN");
        var login = clients.create().bootstrap().post("/api/v1/auth/login",
                Map.of("username", "bruno", "password", Accounts.PASSWORD));
        assertThat(login).hasStatus(401);
    }

    @Test
    @DisplayName("admin promove outra pessoa (não a si mesmo) e então pode excluir a própria conta")
    void shouldPromoteAdminBeforeLeaving() {
        String anaId = ana.json(ana.get("/api/v1/me")).get("account").get("id").asString();
        assertThat(bruno.put("/api/v1/admin/users/" + anaId + "/admin", null)).as("só admin").hasStatus(403);
        assertThat(ana.delete("/api/v1/admin/users/" + anaId + "/admin", null)).hasStatus(409);
        assertThat(ana.put("/api/v1/admin/users/" + brunoId + "/admin", null)).hasStatus(204);
        assertThat(jdbc.queryForObject("select role from user_accounts where username = 'bruno'", String.class))
                .isEqualTo("ADMIN");
        assertThat(bruno.get("/api/v1/me")).as("sessões do promovido encerradas").hasStatus(401);
        assertThat(ana.delete("/api/v1/account", Map.of("password", Accounts.PASSWORD))).hasStatus(204);
    }

    private void send(ApiClient client, String body, String attachmentId) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("clientMessageId", UUID.randomUUID());
        request.put("body", body);
        request.put("attachmentId", attachmentId);
        assertThat(client.post("/api/v1/conversations/" + conversation + "/messages", request)).hasStatus(201);
    }

    private static Map<String, byte[]> unzip(MvcTestResult result) throws IOException {
        assertThat(result).hasStatusOk();
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(
                new ByteArrayInputStream(result.getResponse().getContentAsByteArray()), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                files.put(entry.getName(), zip.readAllBytes());
            }
        }
        return files;
    }
}
