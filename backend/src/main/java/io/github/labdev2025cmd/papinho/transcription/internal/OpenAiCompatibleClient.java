package io.github.labdev2025cmd.papinho.transcription.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cliente da API de transcrição compatível com a OpenAI (AU-05): {@code POST {base}/v1/audio/transcriptions} em
 * multipart com {@code file}, {@code model} e, no Whisper próprio, {@code hotwords}; resposta {@code {"text": ...}}.
 * O User-Agent é {@code Papinho/<versão>}: a Cloudflare bloqueia o padrão {@code OpenAI/...}.
 */
public class OpenAiCompatibleClient {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final JsonMapper json;
    private final String userAgent;

    public OpenAiCompatibleClient(JsonMapper json, String version) {
        this.json = json;
        this.userAgent = "Papinho/" + version;
    }

    public String userAgent() {
        return userAgent;
    }

    /** Requisição de transcrição recusada ou falhou. A mensagem nunca contém a chave. */
    public static class TranscriptionFailedException extends IOException {
        public TranscriptionFailedException(String message) {
            super(message);
        }
    }

    public String transcribe(String baseUrl, @Nullable String apiKey, String model, @Nullable String hotwords,
            String filename, String contentType, InputStream audio) throws IOException {
        String boundary = "papinho-" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        field(body, boundary, "model", model);
        field(body, boundary, "response_format", "json");
        if (hotwords != null && !hotwords.isBlank()) {
            field(body, boundary, "hotwords", hotwords);
        }
        body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                + filename.replace("\"", "") + "\"\r\nContent-Type: " + contentType + "\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        audio.transferTo(body);
        body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint(baseUrl, "/v1/audio/transcriptions"))
                .timeout(Duration.ofMinutes(5)).header("User-Agent", userAgent)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));
        if (apiKey != null && !apiKey.isBlank()) {
            request.header("Authorization", "Bearer " + apiKey);
        }
        HttpResponse<byte[]> response = send(request.build());
        if (response.statusCode() / 100 != 2) {
            throw new TranscriptionFailedException("Provider answered HTTP " + response.statusCode());
        }
        JsonNode node = json.readTree(response.body());
        JsonNode text = node.get("text");
        if (text == null || !text.isString()) {
            throw new TranscriptionFailedException("Provider answer without text");
        }
        return text.asString().strip();
    }

    /** Verificação de saúde do Whisper próprio: {@code GET {base}/health} respondendo 2xx. */
    public boolean healthy(String baseUrl) {
        try {
            HttpResponse<byte[]> response = send(HttpRequest.newBuilder(endpoint(baseUrl, "/health"))
                    .timeout(Duration.ofSeconds(10)).header("User-Agent", userAgent).GET().build());
            return response.statusCode() / 100 == 2;
        } catch (IOException | IllegalArgumentException failed) {
            return false;
        }
    }

    private HttpResponse<byte[]> send(HttpRequest request) throws IOException {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new TranscriptionFailedException("Interrupted");
        }
    }

    static URI endpoint(String baseUrl, String path) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (base.endsWith("/v1") && path.startsWith("/v1/")) {
            base = base.substring(0, base.length() - 3);
        }
        return URI.create(base + path);
    }

    private static void field(ByteArrayOutputStream body, String boundary, String name, String value) {
        body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }
}
