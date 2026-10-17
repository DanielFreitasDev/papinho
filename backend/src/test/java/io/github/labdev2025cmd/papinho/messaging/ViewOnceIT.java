package io.github.labdev2025cmd.papinho.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.labdev2025cmd.papinho.support.Accounts;
import io.github.labdev2025cmd.papinho.support.ApiClient;
import io.github.labdev2025cmd.papinho.support.Images;
import io.github.labdev2025cmd.papinho.support.IntegrationTest;
import io.github.labdev2025cmd.papinho.support.TestSupportConfiguration.ApiClients;
import io.github.labdev2025cmd.papinho.support.Uploads;
import tools.jackson.databind.JsonNode;

/** Visualização única (MS-08): token único por destinatário, no-store, sem id do arquivo e remoção depois. */
@IntegrationTest
class ViewOnceIT {

    @Autowired
    private ApiClients clients;

    @Autowired
    private ViewOnceCleanupJob job;

    @Autowired
    private JdbcTemplate jdbc;

    private ApiClient ana;
    private ApiClient bruno;
    private String conversation;

    @BeforeEach
    void setUp() {
        ana = clients.create();
        Accounts.register(ana, "ana", null);
        bruno = clients.create();
        Accounts.register(bruno, "bruno", Accounts.invite(ana));
        bruno.post("/api/v1/contacts", Map.of("username", "ana"));
        conversation = ana.json(ana.post("/api/v1/conversations/direct", Map.of("username", "bruno")))
                .get("id").asString();
    }

    @Test
    @DisplayName("abre uma vez por token com no-store; ninguém lê pelas rotas de mídia; o arquivo sai depois")
    void shouldOpenOnceAndPurge() {
        String photo = Uploads.upload(ana, Images.png(40, 40), "segredo.png", "image/png");
        JsonNode sent = send(photo, true);
        String id = sent.get("id").asString();
        assertThat(sent.get("attachment").isNull()).as("o id do arquivo não vai para o cliente").isTrue();
        assertThat(sent.get("viewOnce").get("kind").asString()).isEqualTo("IMAGE");
        assertThat(ana.get("/api/v1/media/" + photo + "/content")).as("nem quem enviou").hasStatus(404);
        assertThat(bruno.get("/api/v1/media/" + photo + "/content")).hasStatus(404);
        assertThat(bruno.get("/api/v1/media/" + photo + "/thumbnail")).hasStatus(404);
        assertThat(ana.post("/api/v1/messages/" + id + "/view-once", null)).as("quem enviou não abre")
                .hasStatus(400);
        assertThat(ana.post("/api/v1/messages/forward", Map.of("messageIds", List.of(id), "conversationIds",
                List.of(conversation)))).hasStatus(400);

        JsonNode grant = bruno.json(bruno.post("/api/v1/messages/" + id + "/view-once", null));
        String token = grant.get("token").asString();
        assertThat(grant.get("contentType").asString()).isEqualTo("image/png");
        var content = bruno.get("/api/v1/view-once/" + token);
        assertThat(content).hasStatusOk();
        assertThat(content.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        assertThat(bruno.get("/api/v1/view-once/" + token)).as("foto: o token vale para uma resposta só")
                .hasStatus(404);
        assertThat(ana.get("/api/v1/view-once/" + token)).as("token é de quem recebeu").hasStatus(404);
        assertThat(bruno.post("/api/v1/messages/" + id + "/view-once", null)).as("uma vez só").hasStatus(410);
        JsonNode seen = bruno.json(bruno.get("/api/v1/conversations/" + conversation + "/messages"))
                .get("messages").get(0);
        assertThat(seen.get("viewOnce").get("openedByMe").asBoolean()).isTrue();

        jdbc.update("update view_once_opens set token_expires_at = now() - interval '1 second'");
        assertThat(bruno.get("/api/v1/view-once/" + token)).as("token vencido").hasStatus(404);
        job.purge();
        JsonNode purged = ana.json(ana.get("/api/v1/conversations/" + conversation + "/messages"))
                .get("messages").get(0);
        assertThat(purged.get("viewOnce").get("expired").asBoolean()).isTrue();
        assertThat(purged.get("viewOnce").get("openedCount").asLong()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from media_files where id = ?", Long.class,
                UUID.fromString(photo))).as("arquivo removido").isZero();
    }

    @Test
    @DisplayName("só foto, vídeo ou áudio novos; depois de 14 dias o arquivo sai mesmo sem abrir")
    void shouldValidateAndExpire() {
        String document = Uploads.upload(ana, "texto".getBytes(), "nota.txt", "text/plain");
        Map<String, Object> request = new HashMap<>();
        request.put("clientMessageId", UUID.randomUUID());
        request.put("body", "");
        request.put("attachmentId", document);
        request.put("viewOnce", true);
        assertThat(ana.post(messages(), request)).hasStatus(400);

        String photo = Uploads.upload(ana, Images.png(40, 40), "foto.png", "image/png");
        Uploads.send(ana, conversation, photo, "");
        request.put("clientMessageId", UUID.randomUUID());
        request.put("attachmentId", photo);
        assertThat(ana.post(messages(), request)).as("arquivo já usado em mensagem comum").hasStatus(400);

        String voice = Uploads.upload(ana, Uploads.ogg(4096), "voz.ogg", "audio/ogg");
        String audioMessage = send(voice, true).get("id").asString();
        assertThat(bruno.post("/api/v1/media/" + voice + "/transcript", null))
                .as("áudio de visualização única nunca é transcrito (AU-03)").hasStatus(404);
        assertThat(audioMessage).isNotBlank();

        // O dono transcreve antes de enviar: virando visualização única, o texto não pode ficar no servidor.
        String spoken = Uploads.upload(ana, Uploads.ogg(4096), "falado.ogg", "audio/ogg");
        assertThat(ana.put("/api/v1/media/" + spoken + "/transcript", Map.of("text", "segredo falado uma vez")))
                .hasStatusOk();
        send(spoken, true);
        assertThat(jdbc.queryForObject("select count(*) from transcripts where media_id = ?", Long.class,
                UUID.fromString(spoken))).as("transcrição de áudio que virou visualização única").isZero();

        String other = Uploads.upload(ana, Images.png(40, 40), "outra.png", "image/png");
        String id = send(other, true).get("id").asString();
        jdbc.update("update messages set created_at = now() - interval '15 days' where id = ?", UUID.fromString(id));
        job.purge();
        assertThat(bruno.post("/api/v1/messages/" + id + "/view-once", null)).hasStatus(410);
    }

    private JsonNode send(String attachmentId, boolean viewOnce) {
        Map<String, Object> request = new HashMap<>();
        request.put("clientMessageId", UUID.randomUUID());
        request.put("body", "");
        request.put("attachmentId", attachmentId);
        request.put("viewOnce", viewOnce);
        var result = ana.post(messages(), request);
        assertThat(result).as(ApiClient.text(result)).hasStatus(201);
        return ana.json(result);
    }

    private String messages() {
        return "/api/v1/conversations/" + conversation + "/messages";
    }
}
