package io.github.labdev2025cmd.papinho.messaging;

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
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import io.github.labdev2025cmd.papinho.support.Accounts;
import io.github.labdev2025cmd.papinho.support.ApiClient;
import io.github.labdev2025cmd.papinho.support.Images;
import io.github.labdev2025cmd.papinho.support.IntegrationTest;
import io.github.labdev2025cmd.papinho.support.TestSupportConfiguration.ApiClients;
import io.github.labdev2025cmd.papinho.support.Uploads;

/** Exportar conversa em ZIP no formato do WhatsApp, com mídia opcional (BK-01). */
@IntegrationTest
class ExportIT {

    @Autowired
    private ApiClients clients;

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
        ana.post("/api/v1/contacts", Map.of("username", "bruno", "nickname", "Bruninho"));
        conversation = ana.json(ana.post("/api/v1/conversations/direct", Map.of("username", "bruno")))
                .get("id").asString();
    }

    @Test
    @DisplayName("texto no formato do WhatsApp com os nomes de quem exporta; sem o que apaguei para mim")
    void shouldExportText() throws IOException {
        send(ana, "Oi *Bruno*", null);
        String hidden = send(bruno, "não exporta", null);
        String deleted = send(bruno, "vou apagar", null);
        send(bruno, "Tudo certo!\nSegunda linha", null);
        ana.post("/api/v1/messages/hide", Map.of("messageIds", java.util.List.of(hidden)));
        bruno.delete("/api/v1/messages/" + deleted, null);

        MvcTestResult result = ana.get(path() + "/export");
        assertThat(result).hasStatusOk().hasContentType("application/zip");
        assertThat(result.getResponse().getHeader("Content-Disposition"))
                .contains("attachment").contains("Bruninho");
        assertThat(result.getResponse().getHeader("Cache-Control")).contains("no-store");
        Map<String, byte[]> files = unzip(result);
        assertThat(files).containsOnlyKeys("Conversa.txt");
        String chat = new String(files.get("Conversa.txt"), StandardCharsets.UTF_8);
        assertThat(chat).containsPattern("\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2} - Pessoa ana: Oi \\*Bruno\\*\n")
                .contains(" - Bruninho: Esta mensagem foi apagada\n")
                .contains(" - Bruninho: Tudo certo!\nSegunda linha\n")
                .doesNotContain("não exporta");
        String brunoChat = new String(unzip(bruno.get(path() + "/export")).get("Conversa.txt"),
                StandardCharsets.UTF_8);
        assertThat(brunoChat).as("o Bruno vê com os nomes dele").contains(" - Pessoa ana: Oi").contains("não exporta");
    }

    @Test
    @DisplayName("com mídia, os arquivos vão no ZIP; sem mídia, só a linha; trancada exige o código; IDOR 404")
    void shouldExportMediaAndProtectAccess() throws IOException {
        String upload = Uploads.upload(ana, Images.png(32, 32), "praia.png", "image/png");
        send(ana, "olha a foto", upload);

        Map<String, byte[]> withMedia = unzip(ana.get(path() + "/export?media=true"));
        String file = withMedia.keySet().stream().filter(name -> name.endsWith("praia.png")).findFirst()
                .orElseThrow();
        assertThat(withMedia.get(file)).isNotEmpty();
        assertThat(new String(withMedia.get("Conversa.txt"), StandardCharsets.UTF_8))
                .contains("<anexado: " + file + ">\nolha a foto");
        Map<String, byte[]> without = unzip(ana.get(path() + "/export"));
        assertThat(without).containsOnlyKeys("Conversa.txt");
        assertThat(new String(without.get("Conversa.txt"), StandardCharsets.UTF_8))
                .contains("<arquivo omitido: " + file + ">");

        ApiClient carla = clients.create();
        Accounts.register(carla, "carla", Accounts.invite(ana));
        assertThat(carla.get(path() + "/export")).hasStatus(404);
        ana.put("/api/v1/privacy/locked-conversations/" + conversation, Map.of("code", "4321"));
        assertThat(ana.get(path() + "/export")).as("trancada sem token").hasStatus(403);
    }

    private String path() {
        return "/api/v1/conversations/" + conversation;
    }

    private String send(ApiClient client, String body, String attachmentId) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("clientMessageId", UUID.randomUUID());
        request.put("body", body);
        request.put("attachmentId", attachmentId);
        var result = client.post(path() + "/messages", request);
        assertThat(result).as(ApiClient.text(result)).hasStatus(201);
        return client.json(result).get("id").asString();
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
