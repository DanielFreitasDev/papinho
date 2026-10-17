package io.github.labdev2025cmd.papinho.linkpreview.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/**
 * SSRF na prática contra um servidor local (MD-04). A política dos testes libera só o 127.0.0.1 e a porta do
 * servidor; nomes de DNS e outros endereços de loopback continuam proibidos.
 */
class SafeFetcherTest {

    private static HttpServer server;
    private static int port;
    private static final AtomicInteger HITS = new AtomicInteger();

    @BeforeAll
    static void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        port = server.getAddress().getPort();
        server.createContext("/ok", exchange -> {
            HITS.incrementAndGet();
            byte[] body = "<html><title>Oi</title></html>".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/big", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, 0);
            byte[] chunk = new byte[8192];
            for (int i = 0; i < 64; i++) {
                exchange.getResponseBody().write(chunk);
            }
            exchange.close();
        });
        server.createContext("/drip", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, 0);
            try (var out = exchange.getResponseBody()) {
                for (int i = 0; i < 60; i++) {
                    out.write('<');
                    out.flush();
                    Thread.sleep(250);
                }
            } catch (IOException | InterruptedException aborted) {
                // O cliente desistiu.
            }
        });
        server.createContext("/loop", exchange -> redirect(exchange, "/loop"));
        server.createContext("/to-ok", exchange -> redirect(exchange, "/ok"));
        server.createContext("/to-other-loopback",
                exchange -> redirect(exchange, "http://127.0.0.2:" + port + "/ok"));
        server.createContext("/to-name", exchange -> redirect(exchange, "http://localhost:" + port + "/ok"));
        server.createContext("/to-file", exchange -> redirect(exchange, "file:///etc/passwd"));
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
    }

    @AfterAll
    static void stop() {
        server.stop(0);
    }

    private static void redirect(com.sun.net.httpserver.HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().add("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private static SafeFetcher fetcher() {
        return new SafeFetcher(policy(), "Papinho-test");
    }

    private static AddressPolicy policy() {
        return new AddressPolicy() {
            @Override
            public boolean allowed(InetAddress address) {
                return address.getHostAddress().equals("127.0.0.1");
            }

            @Override
            public boolean allowsPort(int candidate) {
                return candidate == port;
            }
        };
    }

    private static URI url(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    @Test
    @DisplayName("busca permitida e redirecionamento curto funcionam")
    void shouldFetchAllowedUrls() throws IOException {
        try (SafeFetcher fetcher = fetcher()) {
            var response = fetcher.get(url("/to-ok"), "text/html", 1024);
            assertThat(response).isPresent();
            assertThat(new String(response.get().body(), StandardCharsets.UTF_8)).contains("<title>Oi</title>");
            assertThat(response.get().uri().getPath()).isEqualTo("/ok");
        }
    }

    @Test
    @DisplayName("com a regra de produção (só endereços públicos), o servidor local que responde não é alcançado")
    void shouldRefuseLoopbackWithProductionRule() throws IOException {
        AddressPolicy production = new AddressPolicy() {
            @Override
            public boolean allowed(InetAddress address) {
                return AddressPolicy.PUBLIC_ONLY.allowed(address);
            }

            @Override
            public boolean allowsPort(int candidate) {
                return candidate == port;
            }
        };
        try (SafeFetcher fetcher = new SafeFetcher(production, "Papinho-test")) {
            int before = HITS.get();
            assertThat(fetcher.get(url("/ok"), "text/html", 1024)).isEmpty();
            assertThat(fetcher.get(URI.create("http://localhost:" + port + "/ok"), "text/html", 1024)).isEmpty();
            assertThat(HITS.get()).as("o servidor local não recebeu nada").isEqualTo(before);
        }
    }

    @Test
    @DisplayName("redireciona para outro loopback ou para file: e é recusado")
    void shouldBlockForbiddenRedirects() throws IOException {
        try (SafeFetcher fetcher = fetcher()) {
            int before = HITS.get();
            assertThat(fetcher.get(url("/to-other-loopback"), "text/html", 1024)).isEmpty();
            assertThat(fetcher.get(url("/to-file"), "text/html", 1024)).isEmpty();
            assertThat(HITS.get()).as("nenhum destino proibido foi alcançado").isEqualTo(before);
        }
    }

    @Test
    @DisplayName("nome de DNS que resolve para endereço proibido é recusado antes de conectar")
    void shouldCheckAddressesAfterDnsResolution() throws IOException {
        AddressPolicy denyAll = new AddressPolicy() {
            @Override
            public boolean allowed(InetAddress address) {
                return false;
            }

            @Override
            public boolean allowsPort(int candidate) {
                return candidate == port;
            }
        };
        try (SafeFetcher fetcher = new SafeFetcher(denyAll, "Papinho-test")) {
            int before = HITS.get();
            assertThat(fetcher.get(URI.create("http://localhost:" + port + "/ok"), "text/html", 1024)).isEmpty();
            assertThat(HITS.get()).isEqualTo(before);
        }
    }

    @Test
    @DisplayName("corpo que chega aos poucos é abortado no prazo total, apesar do timeout por leitura")
    void shouldAbortDripAtTotalDeadline() throws IOException {
        try (SafeFetcher fetcher = new SafeFetcher(policy(), "Papinho-test", java.time.Duration.ofSeconds(1))) {
            long start = System.nanoTime();
            assertThat(fetcher.get(url("/drip"), "text/html", 1024)).isEmpty();
            assertThat(java.time.Duration.ofNanos(System.nanoTime() - start)).as("15 s de gotejamento")
                    .isLessThan(java.time.Duration.ofSeconds(4));
        }
    }

    @Test
    @DisplayName("limita redirecionamentos, tamanho, esquema, porta e usuário na URL")
    void shouldEnforceLimits() throws IOException {
        try (SafeFetcher fetcher = fetcher()) {
            assertThat(fetcher.get(url("/loop"), "text/html", 1024)).isEmpty();
            var big = fetcher.get(url("/big"), "text/html", 10_000);
            assertThat(big).isPresent();
            assertThat(big.get().truncated()).isTrue();
            assertThat(big.get().body()).hasSize(10_000);
            assertThat(fetcher.acceptable(URI.create("ftp://127.0.0.1/x"))).isFalse();
            assertThat(fetcher.acceptable(URI.create("http://user:pass@127.0.0.1:" + port + "/ok"))).isFalse();
            assertThat(fetcher.acceptable(URI.create("http://127.0.0.1:22/"))).isFalse();
            assertThat(fetcher.acceptable(URI.create("http://[::1]:" + port + "/"))).isFalse();
        }
        try (SafeFetcher publicOnly = new SafeFetcher(AddressPolicy.PUBLIC_ONLY, "Papinho-test")) {
            assertThat(publicOnly.get(URI.create("http://127.0.0.1/"), "text/html", 1024)).isEmpty();
            assertThat(publicOnly.get(URI.create("http://169.254.169.254/latest/meta-data/"), "text/html", 1024))
                    .isEmpty();
        }
    }
}
