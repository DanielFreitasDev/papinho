package io.github.labdev2025cmd.papinho.media.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.labdev2025cmd.papinho.media.MediaProperties;

/**
 * Protocolo {@code zINSTREAM} do clamd contra um servidor falso que responde como o ClamAV (MD-06). A assinatura
 * de teste EICAR é reconhecida pelo servidor falso.
 */
class ClamAvScannerTest {

    private static final String EICAR = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";

    @TempDir
    Path dir;

    @Test
    @DisplayName("arquivo limpo passa; EICAR é recusado; resposta estranha falha fechado")
    void shouldScanThroughInstream() throws Exception {
        Path clean = Files.writeString(dir.resolve("ok.txt"), "conteúdo normal ".repeat(10_000));
        Path infected = Files.writeString(dir.resolve("eicar.txt"), EICAR);

        try (FakeClamd clamd = new FakeClamd(null)) {
            ClamAvScanner scanner = scanner(clamd.port());
            assertThat(scanner.isClean(clean)).isTrue();
            assertThat(clamd.received.get()).hasSize((int) Files.size(clean));
        }
        try (FakeClamd clamd = new FakeClamd(null)) {
            assertThat(scanner(clamd.port()).isClean(infected)).isFalse();
        }
        try (FakeClamd clamd = new FakeClamd("INSTREAM size limit exceeded. ERROR")) {
            ClamAvScanner scanner = scanner(clamd.port());
            assertThatThrownBy(() -> scanner.isClean(clean)).isInstanceOf(IOException.class);
        }
    }

    private static ClamAvScanner scanner(int port) {
        return new ClamAvScanner(new MediaProperties.ClamAv(true, "127.0.0.1", port, Duration.ofSeconds(5)));
    }

    /** clamd de mentira: lê o comando e os blocos e responde OK, FOUND ou a resposta fixa. */
    private static final class FakeClamd implements AutoCloseable {

        private final ServerSocket server = new ServerSocket(0);
        private final AtomicReference<byte[]> received = new AtomicReference<>();
        private final Thread thread;

        FakeClamd(String fixedReply) throws IOException {
            thread = Thread.ofVirtual().start(() -> {
                try (Socket socket = server.accept()) {
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    byte[] command = new byte[10];
                    in.readFully(command);
                    if (!"zINSTREAM\0".equals(new String(command, StandardCharsets.US_ASCII))) {
                        return;
                    }
                    ByteArrayOutputStream data = new ByteArrayOutputStream();
                    int size;
                    while ((size = in.readInt()) > 0) {
                        data.write(in.readNBytes(size));
                    }
                    received.set(data.toByteArray());
                    String body = data.toString(StandardCharsets.ISO_8859_1);
                    String reply = fixedReply != null ? fixedReply
                            : body.contains("EICAR-STANDARD-ANTIVIRUS-TEST-FILE") ? "stream: Eicar-Signature FOUND"
                                    : "stream: OK";
                    socket.getOutputStream().write((reply + "\0").getBytes(StandardCharsets.US_ASCII));
                } catch (IOException ignored) {
                    // O teste falha pela asserção do lado do cliente.
                }
            });
        }

        int port() {
            return server.getLocalPort();
        }

        @Override
        public void close() throws Exception {
            server.close();
            thread.join(2000);
        }
    }
}
