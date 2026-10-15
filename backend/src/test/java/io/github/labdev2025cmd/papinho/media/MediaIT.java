package io.github.labdev2025cmd.papinho.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import io.github.labdev2025cmd.papinho.admin.InstanceSettingsService;
import io.github.labdev2025cmd.papinho.support.Accounts;
import io.github.labdev2025cmd.papinho.support.ApiClient;
import io.github.labdev2025cmd.papinho.support.Images;
import io.github.labdev2025cmd.papinho.support.IntegrationTest;
import io.github.labdev2025cmd.papinho.support.TestSupportConfiguration.ApiClients;
import tools.jackson.databind.JsonNode;

/** Upload tus, detecção pelo conteúdo, imagens sem EXIF, cota, anexos em mensagens e acesso (MD-01, MD-02). */
@IntegrationTest
class MediaIT {

    @Autowired
    private ApiClients clients;

    @Autowired
    private InstanceSettingsService settings;

    private ApiClient ana;
    private ApiClient bruno;
    private ApiClient carla;
    private String conversation;

    @BeforeEach
    void setUp() {
        ana = clients.create();
        Accounts.register(ana, "ana", null);
        bruno = clients.create();
        Accounts.register(bruno, "bruno", Accounts.invite(ana));
        carla = clients.create();
        Accounts.register(carla, "carla", Accounts.invite(ana));
        conversation = ana.json(ana.post("/api/v1/conversations/direct", Map.of("username", "bruno")))
                .get("id").asString();
    }

    @Test
    @DisplayName("tus: cria, retoma pelo HEAD, recusa deslocamento errado e completa em dois pedaços")
    void shouldUploadInChunks() {
        byte[] pdf = ("%PDF-1.4\n" + "conteúdo ".repeat(2000)).getBytes(StandardCharsets.UTF_8);
        assertThat(ana.perform(MockMvcRequestBuilders.post("/api/v1/uploads").header("Upload-Length", pdf.length)))
                .as("sem Tus-Resumable").hasStatus(412);
        String location = create(ana, pdf.length, "../Relatório.pdf", "application/pdf");

        int half = pdf.length / 2;
        MvcTestResult first = patch(ana, location, 0, Arrays.copyOfRange(pdf, 0, half));
        assertThat(first).hasStatus(204);
        assertThat(first.getResponse().getHeader("Upload-Offset")).isEqualTo(String.valueOf(half));
        assertThat(first.getResponse().getHeader("Papinho-Media-Id")).isNull();

        MvcTestResult head = ana.perform(MockMvcRequestBuilders.head(location).header("Tus-Resumable", "1.0.0"));
        assertThat(head.getResponse().getHeader("Upload-Offset")).isEqualTo(String.valueOf(half));
        assertThat(head.getResponse().getHeader("Cache-Control")).contains("no-store");
        assertThat(bruno.perform(MockMvcRequestBuilders.head(location).header("Tus-Resumable", "1.0.0")))
                .as("upload de outra pessoa").hasStatus(404);

        MvcTestResult conflict = patch(ana, location, 0, Arrays.copyOfRange(pdf, half, pdf.length));
        assertThat(conflict).hasStatus(409);
        assertThat(ana.json(conflict).get("offset").asLong()).isEqualTo(half);

        MvcTestResult last = patch(ana, location, half, Arrays.copyOfRange(pdf, half, pdf.length));
        assertThat(last).hasStatus(204);
        String mediaId = last.getResponse().getHeader("Papinho-Media-Id");
        assertThat(mediaId).isEqualTo(location.substring(location.lastIndexOf('/') + 1));

        JsonNode media = ana.json(ana.get("/api/v1/media/" + mediaId));
        assertThat(media.get("kind").asString()).isEqualTo("DOCUMENT");
        assertThat(media.get("contentType").asString()).isEqualTo("application/pdf");
        assertThat(media.get("filename").asString()).isEqualTo("Relatório.pdf");
        assertThat(media.get("size").asLong()).isEqualTo(pdf.length);

        MvcTestResult download = ana.get("/api/v1/media/" + mediaId + "/content");
        assertThat(download).hasStatusOk();
        assertThat(download.getResponse().getContentType()).isEqualTo("application/octet-stream");
        assertThat(download.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment");
        assertThat(download.getResponse().getContentAsByteArray()).isEqualTo(pdf);
    }

    @Test
    @DisplayName("tipo vem do conteúdo, não do nome; JPEG perde o EXIF e ganha miniatura e ThumbHash")
    void shouldDetectTypeAndStripExif() {
        String png = upload(ana, Images.png(64, 32), "nao-sou-imagem.txt", "text/plain");
        JsonNode pngView = ana.json(ana.get("/api/v1/media/" + png));
        assertThat(pngView.get("contentType").asString()).isEqualTo("image/png");
        assertThat(pngView.get("kind").asString()).isEqualTo("IMAGE");
        assertThat(pngView.get("width").asInt()).isEqualTo(64);

        String jpeg = upload(ana, Images.jpegWithOrientation(300, 200, 6), "foto.jpg", "image/jpeg");
        JsonNode view = ana.json(ana.get("/api/v1/media/" + jpeg));
        assertThat(view.get("width").asInt()).as("orientação aplicada").isEqualTo(200);
        assertThat(view.get("height").asInt()).isEqualTo(300);
        assertThat(view.get("thumbhash").asString()).isNotBlank();
        assertThat(view.get("hasThumbnail").asBoolean()).isTrue();
        MvcTestResult content = ana.get("/api/v1/media/" + jpeg + "/content");
        assertThat(content.getResponse().getContentType()).isEqualTo("image/jpeg");
        assertThat(content.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).startsWith("inline");
        assertThat(new String(content.getResponse().getContentAsByteArray(), StandardCharsets.ISO_8859_1))
                .doesNotContain("GPS-SECRETO").doesNotContain("Exif");
        assertThat(ana.get("/api/v1/media/" + jpeg + "/thumbnail")).hasStatusOk();
    }

    @Test
    @DisplayName("anexo na mensagem: a outra pessoa abre com Range; quem é de fora recebe 404")
    void shouldShareAttachmentWithConversationOnly() {
        byte[] video = mp4(4096);
        String id = upload(ana, video, "video.mp4", "video/mp4");
        assertThat(bruno.get("/api/v1/media/" + id)).as("antes de enviar").hasStatus(404);

        Map<String, Object> message = new HashMap<>();
        message.put("clientMessageId", UUID.randomUUID());
        message.put("body", "");
        message.put("attachmentId", id);
        MvcTestResult sent = ana.post("/api/v1/conversations/" + conversation + "/messages", message);
        assertThat(sent).as(ApiClient.text(sent)).hasStatus(201);
        assertThat(ana.json(sent).get("attachment").get("kind").asString()).isEqualTo("VIDEO");

        JsonNode list = bruno.json(bruno.get("/api/v1/conversations?requests=true"));
        assertThat(list.get(0).get("lastMessage").get("attachmentKind").asString()).isEqualTo("VIDEO");
        MvcTestResult range = bruno.perform(MockMvcRequestBuilders.get("/api/v1/media/" + id + "/content")
                .header(HttpHeaders.RANGE, "bytes=0-99"));
        assertThat(range).hasStatus(206);
        assertThat(range.getResponse().getContentAsByteArray()).hasSize(100);
        assertThat(range.getResponse().getContentType()).isEqualTo("video/mp4");

        assertThat(carla.get("/api/v1/media/" + id)).hasStatus(404);
        assertThat(carla.get("/api/v1/media/" + id + "/content")).hasStatus(404);

        Map<String, Object> stolen = new HashMap<>(message);
        stolen.put("clientMessageId", UUID.randomUUID());
        String other = bruno.json(bruno.post("/api/v1/conversations/direct", Map.of("username", "carla")))
                .get("id").asString();
        assertThat(bruno.post("/api/v1/conversations/" + other + "/messages", stolen))
                .as("anexar arquivo de outra pessoa").hasStatus(400);

        String messageId = ana.json(sent).get("id").asString();
        String carlaChat = bruno.json(bruno.post("/api/v1/conversations/direct", Map.of("username", "carla")))
                .get("id").asString();
        assertThat(bruno.post("/api/v1/messages/forward",
                Map.of("messageIds", new String[] {messageId}, "conversationIds", new String[] {carlaChat})))
                .hasStatus(201);
        assertThat(carla.get("/api/v1/media/" + id)).as("encaminhado chega com o anexo").hasStatusOk();
    }

    @Test
    @DisplayName("limite de 1 GB, cota por usuário e cancelamento que devolve a cota")
    void shouldEnforceLimitsAndQuota() {
        assertThat(ana.perform(tusCreate(1_073_741_825L, "grande.bin", null))).hasStatus(413);
        var current = settings.current();
        settings.update(new io.github.labdev2025cmd.papinho.admin.UpdateInstanceSettingsRequest(
                current.registrationMode(), (int) current.usernameChangeCooldown().toDays(),
                (int) current.messageEditWindow().toMinutes(), (int) current.messageDeleteWindow().toHours(), 1,
                current.breachedPasswordCheck()));
        try {
            String location = create(ana, 700_000, "a.bin", null);
            MvcTestResult over = ana.perform(tusCreate(400_000, "b.bin", null));
            assertThat(over).hasStatus(413);
            assertThat(ana.json(over).get("type").asString()).isEqualTo("urn:papinho:problem:quota-exceeded");
            assertThat(ana.perform(MockMvcRequestBuilders.delete(location).header("Tus-Resumable", "1.0.0")))
                    .hasStatus(204);
            assertThat(ana.perform(tusCreate(400_000, "b.bin", null))).hasStatus(201);
        } finally {
            settings.update(new io.github.labdev2025cmd.papinho.admin.UpdateInstanceSettingsRequest(
                    current.registrationMode(), (int) current.usernameChangeCooldown().toDays(),
                    (int) current.messageEditWindow().toMinutes(), (int) current.messageDeleteWindow().toHours(),
                    (int) (current.userQuotaBytes() / (1024 * 1024)), current.breachedPasswordCheck()));
        }
    }

    private String upload(ApiClient client, byte[] content, String filename, String type) {
        String location = create(client, content.length, filename, type);
        MvcTestResult done = patch(client, location, 0, content);
        assertThat(done).as(ApiClient.text(done)).hasStatus(204);
        return done.getResponse().getHeader("Papinho-Media-Id");
    }

    private static String create(ApiClient client, long length, String filename, String type) {
        MvcTestResult created = client.perform(tusCreate(length, filename, type));
        assertThat(created).as(ApiClient.text(created)).hasStatus(201);
        assertThat(created.getResponse().getHeader("Tus-Resumable")).isEqualTo("1.0.0");
        return created.getResponse().getHeader(HttpHeaders.LOCATION);
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder tusCreate(long length,
            String filename, String type) {
        String metadata = "filename " + b64(filename) + (type == null ? "" : ",filetype " + b64(type));
        return MockMvcRequestBuilders.post("/api/v1/uploads").header("Tus-Resumable", "1.0.0")
                .header("Upload-Length", length).header("Upload-Metadata", metadata);
    }

    private static MvcTestResult patch(ApiClient client, String location, long offset, byte[] chunk) {
        return client.perform(MockMvcRequestBuilders.request(HttpMethod.PATCH, location)
                .header("Tus-Resumable", "1.0.0").header("Upload-Offset", offset)
                .contentType("application/offset+octet-stream").content(chunk));
    }

    private static String b64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    /** Começo de um MP4 ("ftyp isom") seguido de bytes quaisquer: o Tika reconhece como video/mp4. */
    private static byte[] mp4(int size) {
        byte[] data = new byte[size];
        byte[] header = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 2, 0, 'i', 's', 'o', 'm',
            'i', 's', 'o', '2'};
        System.arraycopy(header, 0, data, 0, header.length);
        for (int i = header.length; i < size; i++) {
            data[i] = (byte) (i * 31);
        }
        return data;
    }
}
