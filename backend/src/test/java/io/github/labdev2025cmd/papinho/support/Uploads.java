package io.github.labdev2025cmd.papinho.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Atalhos de upload tus e de mensagem com anexo para os testes. */
public final class Uploads {

    private Uploads() {
    }

    /** Sobe o arquivo num só PATCH e devolve o id da mídia. */
    public static String upload(ApiClient client, byte[] content, String filename, String type) {
        String metadata = "filename " + b64(filename) + ",filetype " + b64(type);
        MvcTestResult created = client.perform(MockMvcRequestBuilders.post("/api/v1/uploads")
                .header("Tus-Resumable", "1.0.0").header("Upload-Length", content.length)
                .header("Upload-Metadata", metadata));
        assertThat(created).as(ApiClient.text(created)).hasStatus(201);
        String location = created.getResponse().getHeader(HttpHeaders.LOCATION);
        MvcTestResult done = client.perform(MockMvcRequestBuilders.request(HttpMethod.PATCH, location)
                .header("Tus-Resumable", "1.0.0").header("Upload-Offset", 0)
                .contentType("application/offset+octet-stream").content(content));
        assertThat(done).as(ApiClient.text(done)).hasStatus(204);
        return done.getResponse().getHeader("Papinho-Media-Id");
    }

    /** Envia uma mensagem com anexo (texto pode ser vazio). */
    public static void send(ApiClient client, String conversationId, String attachmentId, String body) {
        Map<String, Object> message = new HashMap<>();
        message.put("clientMessageId", UUID.randomUUID());
        message.put("body", body);
        message.put("attachmentId", attachmentId);
        assertThat(client.post("/api/v1/conversations/" + conversationId + "/messages", message)).hasStatus(201);
    }

    /** Um Ogg mínimo ("OggS"): o Tika reconhece o contêiner e o cliente declara áudio. */
    public static byte[] ogg(int size) {
        byte[] data = new byte[size];
        byte[] header = "OggS".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(header, 0, data, 0, header.length);
        data[4] = 0;
        data[5] = 2;
        for (int i = 6; i < size; i++) {
            data[i] = (byte) (i * 13);
        }
        return data;
    }

    private static String b64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
