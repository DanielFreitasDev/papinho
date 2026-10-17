package io.github.labdev2025cmd.papinho.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.labdev2025cmd.papinho.media.FileStorage;
import io.github.labdev2025cmd.papinho.media.StoredMedia;
import io.github.labdev2025cmd.papinho.support.Accounts;
import io.github.labdev2025cmd.papinho.support.ApiClient;
import io.github.labdev2025cmd.papinho.support.Images;
import io.github.labdev2025cmd.papinho.support.IntegrationTest;
import io.github.labdev2025cmd.papinho.support.TestSupportConfiguration.ApiClients;
import io.github.labdev2025cmd.papinho.support.Uploads;
import tools.jackson.databind.JsonNode;

/** Mensagens temporárias (MS-07): timer por conversa, padrão para conversas novas e o job que apaga. */
@IntegrationTest
class DisappearingIT {

    @Autowired
    private ApiClients clients;

    @Autowired
    private DisappearingMessagesJob job;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private FileStorage storage;

    private ApiClient ana;
    private ApiClient bruno;
    private String brunoId;

    @BeforeEach
    void setUp() {
        ana = clients.create();
        Accounts.register(ana, "ana", null);
        bruno = clients.create();
        brunoId = Accounts.userId(Accounts.register(bruno, "bruno", Accounts.invite(ana)));
        assertThat(ana.post("/api/v1/contacts", Map.of("username", "bruno"))).hasStatus(201);
    }

    @Test
    @DisplayName("timer da conversa vale para as próximas mensagens e deixa um aviso; valores fora da lista dão 400")
    void shouldApplyConversationTimer() {
        String conversation = open(ana, "bruno");
        assertThat(ana.put(path(conversation) + "/disappearing", Map.of("seconds", 123))).hasStatus(400);
        String before = send(ana, conversation, "antes do timer");
        assertThat(ana.put(path(conversation) + "/disappearing", Map.of("seconds", 86_400))).hasStatus(204);

        JsonNode seen = bruno.json(bruno.get(path(conversation)));
        assertThat(seen.get("disappearingSeconds").asInt()).isEqualTo(86_400);
        JsonNode all = bruno.json(bruno.get(path(conversation) + "/messages?afterSeq=0")).get("messages");
        assertThat(all.get(1).get("kind").asString()).isEqualTo("SYSTEM");
        assertThat(all.get(1).get("body").asString()).contains("\"type\":\"timer\"").contains("86400");
        assertThat(all.get(0).get("expiresAt").isNull()).as("mensagem anterior não expira").isTrue();
        assertThat(before).isNotBlank();

        JsonNode after = ana.json(ana.post(path(conversation) + "/messages",
                Map.of("clientMessageId", UUID.randomUUID(), "body", "temporária")));
        Instant expires = Instant.parse(after.get("expiresAt").asString());
        assertThat(expires).isCloseTo(Instant.now().plus(1, ChronoUnit.DAYS), within(1, ChronoUnit.MINUTES));

        Map<String, Object> off = new HashMap<>();
        off.put("seconds", null);
        assertThat(bruno.put(path(conversation) + "/disappearing", off)).hasStatus(204);
        assertThat(ana.json(ana.get(path(conversation))).get("disappearingSeconds").isNull()).isTrue();
    }

    @Test
    @DisplayName("timer padrão vale para conversas e grupos novos; grupo com só admins editando recusa membros")
    void shouldUseDefaultTimerAndGroupPermissions() {
        assertThat(ana.json(ana.put("/api/v1/messaging/preferences", Map.of("defaultDisappearingSeconds", 604_800)))
                .get("defaultDisappearingSeconds").asInt()).isEqualTo(604_800);
        assertThat(ana.json(ana.get("/api/v1/messaging/preferences")).get("defaultDisappearingSeconds").asInt())
                .isEqualTo(604_800);
        String conversation = open(ana, "bruno");
        assertThat(ana.json(ana.get(path(conversation))).get("disappearingSeconds").asInt()).isEqualTo(604_800);

        String group = ana.json(ana.post("/api/v1/groups", Map.of("name", "Temporário", "memberIds",
                List.of(brunoId)))).get("id").asString();
        assertThat(ana.json(ana.get(path(group))).get("disappearingSeconds").asInt()).isEqualTo(604_800);
        ana.put("/api/v1/groups/" + group + "/settings", Map.of("onlyAdminsSend", false, "onlyAdminsEditInfo", true,
                "onlyAdminsAddMembers", false, "joinApproval", false));
        assertThat(bruno.put(path(group) + "/disappearing", Map.of("seconds", 86_400))).hasStatus(403);
    }

    @Test
    @DisplayName("o job apaga as vencidas e os arquivos delas; quem respondeu fica sem a citação")
    void shouldPurgeExpiredMessagesAndFiles() {
        String conversation = open(ana, "bruno");
        ana.put(path(conversation) + "/disappearing", Map.of("seconds", 86_400));
        String photo = Uploads.upload(ana, Images.png(32, 32), "foto.png", "image/png");
        Uploads.send(ana, conversation, photo, "some https://temporaria.invalid/segredo");
        String gone = ana.json(ana.get(path(conversation) + "/messages?limit=1")).get("messages").get(0)
                .get("id").asString();
        String reply = bruno.json(bruno.post(path(conversation) + "/messages",
                Map.of("clientMessageId", UUID.randomUUID(), "body", "vi", "replyToId", gone))).get("id").asString();

        assertThat(bruno.get("/api/v1/link-previews?url=https://temporaria.invalid/segredo")).hasStatusOk();
        jdbc.update("update messages set expires_at = now() - interval '1 minute' where id = ?", UUID.fromString(gone));
        job.purge();
        assertThat(jdbc.queryForObject("select count(*) from link_previews where url like '%temporaria.invalid%'",
                Integer.class)).as("prévia da URL da vencida").isZero();

        JsonNode history = bruno.json(bruno.get(path(conversation) + "/messages")).get("messages");
        assertThat(history.findValuesAsString("id")).doesNotContain(gone).contains(reply);
        JsonNode replyNode = history.get(history.size() - 1);
        assertThat(replyNode.get("replyTo").isNull()).isTrue();
        assertThat(ana.get("/api/v1/media/" + photo + "/content")).as("arquivo removido").hasStatus(404);
        assertThat(StoredMedia.exists(storage, photo)).as("arquivo apagado do armazenamento").isFalse();
    }

    @Test
    @DisplayName("com o job travado por outra instância (ShedLock), nada é apagado; solta a trava, apaga")
    void shouldSkipPurgeWhileAnotherInstanceHoldsTheLock() {
        String conversation = open(ana, "bruno");
        String expired = send(ana, conversation, "vencida");
        jdbc.update("update messages set expires_at = now() - interval '1 minute' where id = ?",
                UUID.fromString(expired));
        jdbc.update("""
                insert into shedlock (name, lock_until, locked_at, locked_by)
                values ('disappearing-messages', now() + interval '5 minutes', now(), 'outra-instancia')
                on conflict (name) do update set lock_until = excluded.lock_until, locked_by = excluded.locked_by
                """);
        try {
            job.purge();
            assertThat(countMessage(expired)).as("outra instância está apagando").isOne();
        } finally {
            jdbc.update("update shedlock set lock_until = now() - interval '1 second' where name = ?",
                    "disappearing-messages");
        }
        job.purge();
        assertThat(countMessage(expired)).isZero();
    }

    private Integer countMessage(String id) {
        return jdbc.queryForObject("select count(*) from messages where id = ?", Integer.class, UUID.fromString(id));
    }

    private String open(ApiClient client, String username) {
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
}
