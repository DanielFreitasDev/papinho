package io.github.labdev2025cmd.papinho.pki;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import java.util.Locale;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import io.github.labdev2025cmd.papinho.TestcontainersConfiguration;
import io.github.labdev2025cmd.papinho.pki.internal.PkiStore;

/**
 * Sobe o servidor real como no perfil {@code lan}: TLS pelo SSL bundle com o certificado de {@code pki init},
 * HTTP/2 e a porta HTTP auxiliar restrita a {@code /setup/**} (PK-02, PK-03, PK-04).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class LanSetupIT {

    private static final Path PKI_DIR;
    private static final int HTTP_PORT;

    static {
        try {
            PKI_DIR = Files.createTempDirectory("papinho-pki-it").resolve("pki");
            HTTP_PORT = freePort();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        var sink = new PrintStream(new ByteArrayOutputStream());
        int status = PkiCommand.run(new String[] {"init", "--dir", PKI_DIR.toString(), "--hostname", "papinho.lan",
                "--ip", "127.0.0.1", "--network", "127.0.0.0/8"}, sink, sink);
        if (status != 0) {
            throw new IllegalStateException("pki init failed with status " + status);
        }
    }

    @DynamicPropertySource
    static void lanProperties(DynamicPropertyRegistry registry) {
        registry.add("server.ssl.bundle", () -> "papinho-lan");
        registry.add("spring.ssl.bundle.pem.papinho-lan.keystore.certificate",
                () -> "file:" + PKI_DIR.resolve(PkiStore.SERVER_CERT));
        registry.add("spring.ssl.bundle.pem.papinho-lan.keystore.private-key",
                () -> "file:" + PKI_DIR.resolve(PkiStore.SERVER_KEY));
        registry.add("app.pki.enabled", () -> "true");
        registry.add("app.pki.dir", PKI_DIR::toString);
        registry.add("app.pki.http-port", () -> String.valueOf(HTTP_PORT));
        registry.add("app.pki.public-http-port", () -> String.valueOf(HTTP_PORT));
    }

    @LocalServerPort
    private int httpsPort;

    private static HttpClient tlsClient;
    private static HttpClient plainClient;
    private static X509Certificate ca;

    @BeforeAll
    static void createClients() throws Exception {
        ca = new PkiStore(PKI_DIR).readCaCertificate();
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("papinho-ca", ca);
        TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trust.init(trustStore);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trust.getTrustManagers(), null);
        tlsClient = HttpClient.newBuilder().sslContext(context).version(HttpClient.Version.HTTP_2).build();
        plainClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    }

    @Test
    @DisplayName("HTTPS com o certificado da PKI responde em HTTP/2 e é validado pela CA da LAN")
    void shouldServeTlsWithHttp2() throws Exception {
        HttpResponse<String> response = tlsClient.send(
                HttpRequest.newBuilder(URI.create("https://127.0.0.1:" + httpsPort + "/actuator/health")).build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.version()).isEqualTo(HttpClient.Version.HTTP_2);
        assertThat(response.body()).contains("UP");
    }

    @Test
    @DisplayName("porta HTTP redireciona a API e o SPA para HTTPS no hostname da PKI")
    void shouldRedirectHttpToHttps() throws Exception {
        HttpResponse<String> api = plain("/api/v1/conversations?x=1");
        HttpResponse<String> root = plain("/");

        assertThat(api.statusCode()).isEqualTo(301);
        assertThat(api.headers().firstValue("Location")).hasValue("https://papinho.lan/api/v1/conversations?x=1");
        assertThat(root.statusCode()).isEqualTo(301);
        assertThat(root.headers().firstValue("Location")).hasValue("https://papinho.lan/");
        assertThat(root.headers().firstValue("Set-Cookie")).isEmpty();
    }

    @Test
    @DisplayName("página /setup na porta HTTP mostra a impressão digital, o QR Code e as instruções por sistema")
    void shouldServeSetupPageOverHttp() throws Exception {
        HttpResponse<String> page = plain("/setup");

        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.headers().firstValue("Content-Type")).hasValueSatisfying(type -> type.startsWith("text/html"));
        assertThat(page.body()).contains(PkiStore.sha256Fingerprint(ca))
                .contains("<svg").contains("Ubuntu/Debian").contains("Fedora").contains("Arch")
                .contains("Windows").contains("macOS").contains("Ajustes de Confiança de Certificado")
                .contains("Android").contains("Firefox")
                .contains("curl -fsSL http://papinho.lan:" + HTTP_PORT + "/setup/install-ca.sh | sh")
                .doesNotContain("<script>").doesNotContain("style=");
        assertThat(page.headers().firstValue("Content-Security-Policy")).hasValueSatisfying(
                csp -> assertThat(csp).contains("script-src 'self'"));
    }

    @Test
    @DisplayName("downloads da CA em DER e PEM batem com o certificado gerado")
    void shouldDownloadCaInDerAndPem() throws Exception {
        HttpResponse<byte[]> der = plainClient.send(request("/setup/ca.crt"), HttpResponse.BodyHandlers.ofByteArray());
        HttpResponse<String> pem = plain("/setup/ca.pem");
        HttpResponse<String> profile = plain("/setup/papinho-ca.mobileconfig");

        assertThat(der.statusCode()).isEqualTo(200);
        assertThat(der.body()).isEqualTo(ca.getEncoded());
        assertThat(der.headers().firstValue("Content-Type")).hasValue("application/x-x509-ca-cert");
        assertThat(der.headers().firstValue("Content-Disposition")).hasValueSatisfying(
                value -> assertThat(value).contains("papinho-ca.crt"));
        assertThat(PkiStore.parseCertificate(der.body())).isEqualTo(ca);
        assertThat(pem.body()).startsWith("-----BEGIN CERTIFICATE-----");
        assertThat(profile.body()).contains("com.apple.security.root")
                .contains(java.util.Base64.getEncoder().encodeToString(ca.getEncoded()));
    }

    @Test
    @DisplayName("install-ca.sh embute a URL da CA e a impressão digital SHA-256 esperada")
    void shouldServeInstallScriptWithFingerprint() throws Exception {
        HttpResponse<String> script = plain("/setup/install-ca.sh");
        String expected = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(ca.getEncoded())).toLowerCase(Locale.ROOT);

        assertThat(script.statusCode()).isEqualTo(200);
        assertThat(script.body()).startsWith("#!/bin/sh")
                .contains("EXPECTED_FINGERPRINT='" + expected + "'")
                .contains("CA_URL='http://papinho.lan:" + HTTP_PORT + "/setup/ca.pem'")
                .contains("--uninstall")
                .doesNotContain("@@");
    }

    @Test
    @DisplayName("cookie XSRF-TOKEN sai Secure, SameSite=Strict e legível pelo SPA no servidor real")
    void shouldIssueStrictCsrfCookieOverTls() throws Exception {
        HttpResponse<String> response = tlsClient.send(
                HttpRequest.newBuilder(URI.create("https://127.0.0.1:" + httpsPort + "/api/v1/instance")).build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.headers().allValues("Set-Cookie")).anySatisfy(cookie -> assertThat(cookie)
                .startsWith("XSRF-TOKEN=").contains("Secure").contains("SameSite=Strict").contains("Path=/")
                .doesNotContain("HttpOnly"));
    }

    @Test
    @DisplayName("porta HTTP não aceita escrita nem em /setup")
    void shouldRejectWritesOnSetup() throws Exception {
        HttpResponse<String> response = plainClient.send(HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + HTTP_PORT + "/setup")).POST(HttpRequest.BodyPublishers.noBody())
                .build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isIn(401, 403);
    }

    private HttpResponse<String> plain(String path) throws Exception {
        return plainClient.send(request(path), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpRequest request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + HTTP_PORT + path)).build();
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
