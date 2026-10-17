package io.github.labdev2025cmd.papinho.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import io.github.labdev2025cmd.papinho.TestcontainersConfiguration;

/**
 * Pedidos que o Tomcat ou o StrictHttpFirewall recusam antes dos controllers também saem em Problem Details e com
 * os cabeçalhos de segurança (SG-01, SG-02). Vão por socket cru: um cliente HTTP normalizaria o caminho.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class RejectedRequestsIT {

    @LocalServerPort
    private int port;

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
        "GET, /api//v1/me, 400",
        "GET, /api/v1/me;jsessionid=x, 400",
        "GET, /api/v1/me%00, 400",
        "GET, /api/v1/%2e%2e/me, 400",
        "FOO, /api/v1/me, 400",
        "TRACE, /api/v1/me, 405"})
    void shouldAnswerRejectedRequestsWithProblemDetails(String method, String path, int status) throws IOException {
        String response = raw(method + " " + path + " HTTP/1.1\r\nHost: localhost\r\nAccept: */*\r\n"
                + "Connection: close\r\n\r\n");
        String head = response.substring(0, response.indexOf("\r\n\r\n")).toLowerCase(Locale.ROOT);
        String body = response.substring(response.indexOf("\r\n\r\n") + 4);
        assertThat(head).startsWith("http/1.1 " + status);
        assertThat(head).as("tipo").contains("content-type: application/problem+json");
        assertThat(body).as("corpo").contains("\"type\":\"urn:papinho:problem:").doesNotContain("<html")
                .doesNotContain("timestamp").doesNotContain("Tomcat");
        assertThat(head).as("cabeçalhos de segurança").contains("x-content-type-options: nosniff")
                .contains("content-security-policy: ").contains("x-frame-options: deny");
    }

    private String raw(String request) throws IOException {
        try (Socket socket = new Socket("localhost", port)) {
            OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            InputStream in = socket.getInputStream();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
