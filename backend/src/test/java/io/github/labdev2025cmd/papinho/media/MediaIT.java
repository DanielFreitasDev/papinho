package io.github.labdev2025cmd.papinho.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import io.github.labdev2025cmd.papinho.admin.InstanceSettings;
import io.github.labdev2025cmd.papinho.admin.InstanceSettingsService;
import io.github.labdev2025cmd.papinho.admin.UpdateInstanceSettingsRequest;
import io.github.labdev2025cmd.papinho.support.Accounts;
import io.github.labdev2025cmd.papinho.support.ApiClient;
import io.github.labdev2025cmd.papinho.support.Images;
import io.github.labdev2025cmd.papinho.support.IntegrationTest;
import io.github.labdev2025cmd.papinho.support.TestSupportConfiguration.ApiClients;
import io.github.labdev2025cmd.papinho.support.Uploads;
import tools.jackson.databind.JsonNode;

/** Upload tus, detecção pelo conteúdo, imagens sem EXIF, cota, anexos em mensagens e acesso (MD-01, MD-02). */
@IntegrationTest
class MediaIT {

    /** WebP mínimo (sem perdas, 1×1), aceito como figurinha. */
    private static final byte[] WEBP = Base64.getDecoder().decode("UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==");

    @Autowired
    private ApiClients clients;

    @Autowired
    private InstanceSettingsService settings;

    @Autowired
    private UnusedFileCleanupJob unusedFiles;

    @Autowired
    private FileStorage storage;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private org.springframework.core.env.Environment environment;

    @Autowired
    private io.github.labdev2025cmd.papinho.support.SwitchableScanner scanner;

    private ApiClient ana;
    private String brunoId;
    private ApiClient bruno;
    private ApiClient carla;
    private String conversation;

    @BeforeEach
    void setUp() {
        ana = clients.create();
        Accounts.register(ana, "ana", null);
        bruno = clients.create();
        brunoId = Accounts.userId(Accounts.register(bruno, "bruno", Accounts.invite(ana)));
        carla = clients.create();
        Accounts.register(carla, "carla", Accounts.invite(ana));
        conversation = ana.json(ana.post("/api/v1/conversations/direct", Map.of("username", "bruno")))
                .get("id").asString();
    }

    @Test
    @DisplayName("antivírus fora do ar: 503 em Problem Details e o upload completo é descartado (MD-06)")
    void shouldAnswerServiceUnavailableWhenScannerIsDown() {
        byte[] content = "conteúdo qualquer".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var created = ana.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/uploads").header("Tus-Resumable", "1.0.0").header("Upload-Length", content.length)
                .header("Upload-Metadata", "filename " + java.util.Base64.getEncoder().encodeToString("a.txt"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        assertThat(created).hasStatus(201);
        String location = created.getResponse().getHeader(org.springframework.http.HttpHeaders.LOCATION);
        scanner.down(true);
        try {
            var done = ana.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .request(org.springframework.http.HttpMethod.PATCH, location).header("Tus-Resumable", "1.0.0")
                    .header("Upload-Offset", 0).contentType("application/offset+octet-stream").content(content));
            assertThat(done).hasStatus(503);
            assertThat(ana.json(done).get("type").asString()).isEqualTo("urn:papinho:problem:media-scanner-unavailable");
            assertThat(ana.json(done).get("detail").asString()).contains("antivírus");
        } finally {
            scanner.down(false);
        }
        assertThat(ana.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head(location)
                .header("Tus-Resumable", "1.0.0"))).as("upload descartado").hasStatus(404);
        assertThat(jdbc.queryForObject("select count(*) from media_files", Integer.class)).isZero();
    }

    @Test
    @DisplayName("os testes gravam arquivos, uploads e a chave fora do diretório real do usuário")
    void shouldKeepTestDataOutOfUserHome() {
        java.nio.file.Path home = java.nio.file.Path.of(System.getProperty("user.home")).toAbsolutePath();
        for (String property : java.util.List.of("app.storage.dir", "app.media.upload-dir", "app.crypto.key-file")) {
            assertThat(java.nio.file.Path.of(environment.getRequiredProperty(property)).toAbsolutePath().normalize())
                    .as(property).satisfies(path -> assertThat(path.startsWith(home.resolve(".papinho"))).isFalse());
        }
    }

    @Test
    @DisplayName("uso de disco só para o admin: bytes dos arquivos, banco, espaço livre e quem mais ocupa")
    void shouldReportStorageUsageToAdmin() {
        Uploads.upload(bruno, Images.png(40, 40), "a.png", "image/png");
        assertThat(bruno.get("/api/v1/admin/storage")).hasStatus(403);
        var usage = ana.json(ana.get("/api/v1/admin/storage"));
        assertThat(usage.get("filesBytes").asLong()).isPositive();
        assertThat(usage.get("fileCount").asLong()).isEqualTo(1);
        assertThat(usage.get("databaseBytes").asLong()).isPositive();
        assertThat(usage.get("freeBytes").asLong()).isPositive();
        assertThat(usage.get("top").get(0).get("username").asString()).isEqualTo("bruno");
    }

    @Test
    @DisplayName("limite de uploads por pessoa (SG-03): 200 por hora, depois 429 com Retry-After; outra pessoa segue")
    void shouldRateLimitUploads() {
        // O balde reabastece aos poucos durante o laço: conta quantos passam até o primeiro 429.
        int accepted = 0;
        MvcTestResult limited = ana.perform(tusCreate(10, "a.txt", "text/plain"));
        while (limited.getResponse().getStatus() == 201 && accepted < 250) {
            accepted++;
            limited = ana.perform(tusCreate(10, "a.txt", "text/plain"));
        }
        assertThat(accepted).isBetween(200, 210);
        assertThat(limited).hasStatus(429);
        assertThat(limited.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isNotBlank();
        assertThat(bruno.perform(tusCreate(10, "b.txt", "text/plain"))).hasStatus(201);
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
    @DisplayName("foto grande demais para recodificar sai sem EXIF; HEIC e WebP grandes, que não dá para limpar, são recusados (MD-02)")
    void shouldStripOrRefusePhotosOutsideThePipeline() {
        int padding = 51 * 1024 * 1024;
        byte[] smallJpeg = Images.jpegWithExif(64, 64);
        String jpeg = upload(ana, Arrays.copyOf(smallJpeg, smallJpeg.length + padding), "grande.jpg", "image/jpeg");
        assertThat(ana.json(ana.get("/api/v1/media/" + jpeg)).get("kind").asString()).isEqualTo("DOCUMENT");
        assertThat(new String(ana.get("/api/v1/media/" + jpeg + "/content").getResponse().getContentAsByteArray(),
                StandardCharsets.ISO_8859_1)).as("JPEG acima do limite").doesNotContain("GPS-SECRETO")
                .doesNotContain("Exif");

        byte[] smallPng = Images.pngWithText(64, 64, "GPS-SECRETO -3.7319,-38.5267");
        String png = upload(ana, Arrays.copyOf(smallPng, smallPng.length + padding), "grande.png", "image/png");
        assertThat(new String(ana.get("/api/v1/media/" + png + "/content").getResponse().getContentAsByteArray(),
                StandardCharsets.ISO_8859_1)).as("PNG acima do limite").doesNotContain("GPS-SECRETO");

        byte[] heic = new byte[4096];
        byte[] box = "\0\0\0\u0018ftypheic\0\0\0\0mif1heic".getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(box, 0, heic, 0, box.length);
        String location = create(ana, heic.length, "foto.heic", "image/heic");
        MvcTestResult refused = patch(ana, location, 0, heic);
        assertThat(refused).as(ApiClient.text(refused)).hasStatus(415);
        assertThat(ana.json(refused).get("type").asString()).isEqualTo("urn:papinho:problem:photo-metadata");
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
        for (String invalid : List.of("bytes=abc", "bytes=9999999-")) {
            MvcTestResult refused = bruno.perform(MockMvcRequestBuilders.get("/api/v1/media/" + id + "/content")
                    .header(HttpHeaders.RANGE, invalid));
            assertThat(refused).as(invalid).hasStatus(416)
                    .hasContentTypeCompatibleWith(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON);
            assertThat(refused.getResponse().getHeader(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes */4096");
            assertThat(bruno.json(refused).get("type").asString()).isEqualTo("urn:papinho:problem:range-not-satisfiable");
            assertThat(refused.getResponse().getContentAsByteArray()).as("sem o arquivo no corpo")
                    .hasSizeLessThan(1000);
        }

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
    @DisplayName("galeria separa mídia, documentos, áudios e links; só quem participa vê")
    void shouldListGalleryByTab() {
        String photo = upload(ana, Images.png(40, 40), "foto.png", "image/png");
        String doc = upload(ana, "%PDF-1.4 doc".getBytes(StandardCharsets.US_ASCII), "doc.pdf", "application/pdf");
        sendWith(ana, photo, "");
        sendWith(ana, doc, "contrato");
        sendWith(ana, null, "veja https://example.com/a e mais");
        sendWith(ana, null, "sem link nenhum");

        String gallery = "/api/v1/conversations/" + conversation + "/gallery?tab=";
        JsonNode media = bruno.json(bruno.get(gallery + "MEDIA"));
        assertThat(media.get("messages")).hasSize(1);
        assertThat(media.get("messages").get(0).get("attachment").get("id").asString()).isEqualTo(photo);
        JsonNode docs = bruno.json(bruno.get(gallery + "DOCUMENTS"));
        assertThat(docs.get("messages").get(0).get("body").asString()).isEqualTo("contrato");
        JsonNode links = bruno.json(bruno.get(gallery + "LINKS"));
        assertThat(links.get("messages")).hasSize(1);
        assertThat(bruno.json(bruno.get(gallery + "AUDIO")).get("messages")).isEmpty();
        assertThat(carla.get(gallery + "MEDIA")).hasStatus(404);
    }

    @Test
    @DisplayName("limite de 1 GB, cota por usuário e cancelamento que devolve a cota")
    void shouldEnforceLimitsAndQuota() {
        assertThat(ana.perform(tusCreate(1_073_741_825L, "grande.bin", null))).hasStatus(413);
        var current = settings.current();
        updateQuota(current, 1);
        try {
            String location = create(ana, 700_000, "a.bin", null);
            MvcTestResult over = ana.perform(tusCreate(400_000, "b.bin", null));
            assertThat(over).hasStatus(413);
            assertThat(ana.json(over).get("type").asString()).isEqualTo("urn:papinho:problem:quota-exceeded");
            assertThat(ana.perform(MockMvcRequestBuilders.delete(location).header("Tus-Resumable", "1.0.0")))
                    .hasStatus(204);
            assertThat(ana.perform(tusCreate(400_000, "b.bin", null))).hasStatus(201);
        } finally {
            updateQuota(current, (int) (current.userQuotaBytes() / (1024 * 1024)));
        }
    }

    @Test
    @DisplayName("cota não estoura com uploads da mesma pessoa criados ao mesmo tempo")
    void shouldEnforceQuotaUnderConcurrentCreates() throws Exception {
        var current = settings.current();
        updateQuota(current, 1);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<ApiClient> devices = IntStream.range(0, 4).mapToObj(index -> {
                ApiClient device = clients.create().bootstrap();
                assertThat(device.post("/api/v1/auth/login", Map.of("username", "ana", "password",
                        Accounts.PASSWORD))).hasStatusOk();
                return device;
            }).toList();
            CountDownLatch start = new CountDownLatch(1);
            List<CompletableFuture<Integer>> creates = devices.stream()
                    .map(device -> CompletableFuture.supplyAsync(() -> {
                        try {
                            start.await(10, TimeUnit.SECONDS);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                        }
                        return device.perform(tusCreate(400_000, "x.bin", null)).getResponse().getStatus();
                    }, executor)).toList();
            start.countDown();
            List<Integer> statuses = creates.stream().map(CompletableFuture::join).toList();
            assertThat(statuses).as("1 MB de cota cabe dois de 400 KB").containsOnly(201, 413)
                    .filteredOn(status -> status == 201).hasSize(2);
        } finally {
            executor.shutdownNow();
            updateQuota(current, (int) (current.userQuotaBytes() / (1024 * 1024)));
        }
    }

    @Test
    @DisplayName("arquivo que nunca virou anexo, figurinha ou foto de grupo sai do servidor depois de 24 h e devolve a cota")
    void shouldPurgeFilesNeverUsed() throws Exception {
        var current = settings.current();
        updateQuota(current, 1);
        try {
            String forgotten = upload(ana, new byte[700_000], "esquecido.bin", null);
            assertThat(ana.perform(tusCreate(400_000, "outro.bin", null))).as("cota ocupada").hasStatus(413);
            String recent = upload(ana, "recente".getBytes(StandardCharsets.UTF_8), "recente.txt", "text/plain");
            String attached = upload(ana, Images.png(20, 20), "anexo.png", "image/png");
            sendWith(ana, attached, "");
            String pack = ana.json(ana.post("/api/v1/stickers/packs", Map.of("name", "Gatos"))).get("id").asString();
            String sticker = upload(ana, WEBP, "gato.webp", "image/webp");
            assertThat(ana.post("/api/v1/stickers/packs/" + pack + "/stickers", Map.of("mediaId", sticker)))
                    .hasStatus(201);
            assertThat(ana.post("/api/v1/contacts", Map.of("username", "bruno"))).hasStatus(201);
            String group = ana.json(ana.post("/api/v1/groups", Map.of("name", "Fotos", "memberIds", List.of(brunoId))))
                    .get("id").asString();
            String photo = upload(ana, Images.png(64, 64), "grupo.png", "image/png");
            assertThat(ana.put("/api/v1/groups/" + group + "/photo", Map.of("mediaId", photo))).hasStatusOk();

            unusedFiles.purge();
            assertThat(ana.get("/api/v1/media/" + forgotten)).as("ainda dentro do prazo").hasStatusOk();

            jdbc.update("update media_files set created_at = now() - interval '25 hours' where id <> ?",
                    UUID.fromString(recent));
            unusedFiles.purge();

            assertThat(ana.get("/api/v1/media/" + forgotten)).hasStatus(404);
            assertThat(storage.resource(MediaFile.contentKey(UUID.fromString(forgotten)))).isEmpty();
            assertThat(ana.perform(tusCreate(400_000, "outro.bin", null))).as("cota devolvida").hasStatus(201);
            assertThat(ana.get("/api/v1/media/" + recent)).as("recente").hasStatusOk();
            assertThat(bruno.get("/api/v1/media/" + attached + "/content")).as("anexo").hasStatusOk();
            assertThat(bruno.get("/api/v1/media/" + sticker + "/content")).as("figurinha").hasStatusOk();
            assertThat(bruno.get("/api/v1/media/" + photo + "/thumbnail")).as("foto do grupo").hasStatusOk();
            assertThat(jdbc.queryForList("select id from media_files where in_use", UUID.class))
                    .as("conferidos uma vez só").containsExactlyInAnyOrder(UUID.fromString(attached),
                            UUID.fromString(sticker), UUID.fromString(photo));
        } finally {
            updateQuota(current, (int) (current.userQuotaBytes() / (1024 * 1024)));
        }
    }

    private void updateQuota(InstanceSettings current, int megabytes) {
        settings.update(new UpdateInstanceSettingsRequest(current.registrationMode(),
                (int) current.usernameChangeCooldown().toDays(), (int) current.messageEditWindow().toMinutes(),
                (int) current.messageDeleteWindow().toHours(), megabytes, current.breachedPasswordCheck()));
    }

    private void sendWith(ApiClient client, String attachmentId, String body) {
        Map<String, Object> message = new HashMap<>();
        message.put("clientMessageId", UUID.randomUUID());
        message.put("body", body);
        message.put("attachmentId", attachmentId);
        assertThat(client.post("/api/v1/conversations/" + conversation + "/messages", message)).hasStatus(201);
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
