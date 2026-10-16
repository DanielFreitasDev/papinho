package io.github.labdev2025cmd.papinho.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import io.github.labdev2025cmd.papinho.TestcontainersConfiguration;

/**
 * Atrás de proxy reverso (produção) com {@code server.forward-headers-strategy=native}: o Tomcat lê o
 * X-Forwarded-For de trás para a frente e só confia nos proxies internos, então o IP que o cliente inventa no começo
 * do cabeçalho (ou num Forwarded) não troca a chave dos limites de tentativa (SG-03).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.forward-headers-strategy=native")
@Import(TestcontainersConfiguration.class)
class ForwardedHeadersIT {

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("com native, X-Forwarded-For e Forwarded inventados pelo cliente não burlam o limite de login por IP")
    void shouldKeepTheClientIpBehindAProxy() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> bootstrap = client.send(HttpRequest.newBuilder(uri("/api/v1/instance"))
                .header("X-Forwarded-Proto", "https").GET().build(), HttpResponse.BodyHandlers.ofString());
        String xsrf = cookie(bootstrap, "XSRF-TOKEN");

        int attempts = 0;
        int status = 0;
        while (status != 429 && attempts < 30) {
            attempts++;
            HttpRequest login = HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                    .header("Content-Type", "application/json")
                    .header("Cookie", "XSRF-TOKEN=" + xsrf)
                    .header("X-XSRF-TOKEN", xsrf)
                    .header("X-Forwarded-Proto", "https")
                    // O cliente inventa um IP a cada tentativa; o proxy acrescenta o de verdade no fim.
                    .header("X-Forwarded-For", "198.51.100." + attempts + ", 203.0.113.9")
                    .header("Forwarded", "for=192.0.2." + attempts)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"username\":\"ninguem" + attempts + "\",\"password\":\"senha errada de teste\"}"))
                    .build();
            status = client.send(login, HttpResponse.BodyHandlers.ofString()).statusCode();
        }
        assertThat(status).isEqualTo(429);
        assertThat(attempts).as("o balde é o do IP que o proxy viu").isEqualTo(
                RateLimiter.Policy.LOGIN_BY_IP.capacity() + 1);
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static String cookie(HttpResponse<?> response, String name) {
        return response.headers().allValues("Set-Cookie").stream()
                .filter(header -> header.startsWith(name + "="))
                .map(header -> header.substring(name.length() + 1, header.indexOf(';')))
                .findFirst().orElseThrow();
    }
}
