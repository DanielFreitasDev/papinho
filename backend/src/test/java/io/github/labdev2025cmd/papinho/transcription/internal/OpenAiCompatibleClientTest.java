package io.github.labdev2025cmd.papinho.transcription.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.json.JsonMapper;

/** Envio do áudio ao provedor (AU-05) contra um servidor local que só conta os bytes. */
class OpenAiCompatibleClientTest {

    private static HttpServer server;
    private static final AtomicLong RECEIVED = new AtomicLong();
    private static final AtomicReference<String> LENGTH = new AtomicReference<>();

    @BeforeAll
    static void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v1/audio/transcriptions", exchange -> {
            LENGTH.set(exchange.getRequestHeaders().getFirst("Content-Length"));
            try (InputStream in = exchange.getRequestBody()) {
                byte[] buffer = new byte[1 << 16];
                long total = 0;
                for (int read; (read = in.read(buffer)) > 0;) {
                    total += read;
                }
                RECEIVED.set(total);
            }
            byte[] body = "{\"text\":\" ok \"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterAll
    static void stop() {
        server.stop(0);
    }

    @Test
    @DisplayName("áudio maior que um array Java vai em streaming, com Content-Length, sem caber na memória (AU-05)")
    void shouldStreamAudioLargerThanAnArray() throws IOException {
        long size = Integer.MAX_VALUE + 64L * 1024 * 1024;
        OpenAiCompatibleClient client = new OpenAiCompatibleClient(JsonMapper.builder().build(), "test");
        String text = client.transcribe("http://127.0.0.1:" + server.getAddress().getPort(), null, "whisper-1",
                null, "voz.wav", "audio/wav", new Zeros(size), size);
        assertThat(text).isEqualTo("ok");
        assertThat(RECEIVED.get()).as("corpo recebido").isGreaterThan(size);
        assertThat(LENGTH.get()).as("Content-Length").isEqualTo(String.valueOf(RECEIVED.get()));
    }

    /** {@code size} bytes que não existem na memória. */
    private static final class Zeros extends InputStream {
        private long left;

        Zeros(long size) {
            this.left = size;
        }

        @Override
        public int read() {
            if (left == 0) {
                return -1;
            }
            left--;
            return 0;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            if (left == 0) {
                return -1;
            }
            int count = (int) Math.min(length, left);
            left -= count;
            return count;
        }
    }
}
