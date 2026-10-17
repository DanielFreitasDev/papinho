package io.github.labdev2025cmd.papinho.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import io.github.labdev2025cmd.papinho.support.Accounts;
import io.github.labdev2025cmd.papinho.support.ApiClient;
import io.github.labdev2025cmd.papinho.support.IntegrationTest;
import io.github.labdev2025cmd.papinho.support.TestSupportConfiguration.ApiClients;
import io.github.labdev2025cmd.papinho.support.TestSupportConfiguration.FailingController;

/** Cadeia de segurança real contra o PostgreSQL do Testcontainers: deny by default, Problem Details e cabeçalhos. */
@IntegrationTest
class SecurityIT {

    @Autowired
    private ApiClients clients;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("health é público e responde UP com o banco migrado")
    void shouldExposeHealth() {
        assertThat(mvc.get().uri("/actuator/health")).hasStatusOk().bodyJson()
                .extractingPath("$.status").isEqualTo("UP");
    }

    @Test
    @DisplayName("endpoints sensíveis do actuator exigem admin")
    void shouldProtectSensitiveActuatorEndpoints() {
        assertThat(mvc.get().uri("/actuator/env")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("API sem sessão responde 401 em Problem Details, sem desafio Basic")
    void shouldRejectAnonymousApiWithProblemDetails() {
        var result = mvc.get().uri("/api/v1/conversations").header(HttpHeaders.ACCEPT_LANGUAGE, "pt-BR");

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .doesNotContainHeader(HttpHeaders.WWW_AUTHENTICATE);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("urn:papinho:problem:unauthorized");
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Não autenticado");
    }

    @Test
    @DisplayName("mensagens de erro seguem o Accept-Language (en)")
    void shouldTranslateProblemDetails() {
        assertThat(mvc.get().uri("/api/v1/conversations").header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .bodyJson().extractingPath("$.title").isEqualTo("Not authenticated");
    }

    @Test
    @DisplayName("POST na API sem token CSRF é recusado")
    void shouldRequireCsrfOnApi() {
        assertThat(mvc.post().uri("/api/v1/conversations")).hasStatus(HttpStatus.FORBIDDEN)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    @DisplayName("API entrega o cookie XSRF-TOKEN legível pelo SPA já na primeira resposta")
    void shouldIssueCsrfCookie() {
        var result = mvc.get().uri("/api/v1/instance").exchange();

        // O MockMvc não serializa o atributo SameSite; ele é verificado no servidor real em LanSetupIT.
        assertThat(result.getMvcResult().getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .anySatisfy(cookie -> assertThat(cookie).startsWith("XSRF-TOKEN=").contains("Secure")
                        .contains("Path=/").doesNotContain("HttpOnly"));
    }

    @Test
    @DisplayName("rota do SPA devolve index.html com CSP estrita e cabeçalhos de segurança")
    void shouldServeSpaShellWithSecurityHeaders() {
        var result = mvc.get().uri("/conversations/42").exchange();

        assertThat(result).hasStatusOk().bodyText().contains("<div id=\"root\">");
        assertThat(result).hasHeader("X-Content-Type-Options", "nosniff")
                .hasHeader("X-Frame-Options", "DENY")
                .hasHeader("Referrer-Policy", "same-origin")
                .hasHeader("Cross-Origin-Opener-Policy", "same-origin");
        String csp = result.getMvcResult().getResponse().getHeader("Content-Security-Policy");
        assertThat(csp).contains("default-src 'self'").contains("script-src 'self'")
                .contains("frame-ancestors 'none'").doesNotContain("unsafe-inline")
                // Só WebAssembly (runtime ONNX da transcrição no navegador, AU-04); eval de JavaScript continua proibido.
                .doesNotContain("'unsafe-eval'").contains("'wasm-unsafe-eval'");
        // A política inteira, escrita aqui: comparar com a constante deixaria passar a perda de uma diretiva nela.
        assertThat(csp).isEqualTo("default-src 'self'; script-src 'self' 'wasm-unsafe-eval'; style-src 'self'; "
                + "img-src 'self' data: blob:; media-src 'self' blob:; font-src 'self'; "
                + "connect-src 'self' https://huggingface.co https://*.huggingface.co https://*.hf.co; "
                + "worker-src 'self' blob:; manifest-src 'self'; "
                + "frame-src https://www.youtube-nocookie.com https://player.vimeo.com https://open.spotify.com; "
                + "object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'");
        assertThat(result).hasHeader("Permissions-Policy",
                "camera=(self), microphone=(self), geolocation=(), payment=(), usb=(), interest-cohort=()");
        assertThat(result).doesNotContainHeader("Strict-Transport-Security");
    }

    @Test
    @DisplayName("rota do SPA com ponto no caminho (@usuario com ponto) também devolve o index.html")
    void shouldServeSpaForRoutesWithDots() {
        assertThat(mvc.get().uri("/add/ana.silva")).hasStatusOk().bodyText().contains("<div id=\"root\">");
        assertThat(mvc.get().uri("/login")).as("rota do SPA, não o endpoint de passkey").hasStatusOk();
        assertThat(mvc.get().uri("/register?invite=abc")).hasStatusOk();
        assertThat(mvc.get().uri("/assets/inexistente.css")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("asset com hash tem cache imutável; arquivo inexistente é 404")
    void shouldCacheHashedAssets() {
        assertThat(mvc.get().uri("/assets/app-abc123.js")).hasStatusOk()
                .hasHeader(HttpHeaders.CACHE_CONTROL, "max-age=31536000, immutable");
        assertThat(mvc.get().uri("/missing.js")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("requisição fora de qualquer cadeia conhecida cai no denyAll")
    void shouldDenyUnknownRequests() {
        assertThat(mvc.post().uri("/conversations/42").exchange().getResponse().getStatus()).isIn(401, 403);
        assertThat(mvc.get().uri("/ws")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.delete().uri("/").exchange().getResponse().getStatus()).isIn(401, 403);
    }

    @Test
    @DisplayName("PKI desligada: /setup não existe (produção, PK-06)")
    void shouldNotExposeSetupWhenPkiDisabled() {
        assertThat(mvc.get().uri("/setup")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/setup/install-ca.sh")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("senhas usam Argon2")
    void shouldHashPasswordsWithArgon2() {
        assertThat(passwordEncoder).isInstanceOf(Argon2PasswordEncoder.class);
        assertThat(passwordEncoder.encode("uma senha bem longa")).startsWith("$argon2id$");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("logs de cadastro, login, envio e erro não têm @usuario, senha nem texto da mensagem (SG-01)")
    void shouldKeepPersonalDataOutOfLogs(CapturedOutput output) {
        ApiClient ana = clients.create();
        Accounts.register(ana, "ana.registrada", null);
        ApiClient bruno = clients.create();
        Accounts.register(bruno, "bruno.registrado",
                Accounts.invite(ana));
        ana.post("/api/v1/auth/logout", null);
        ana.clearCookies();
        ana.bootstrap();
        assertThat(ana.post("/api/v1/auth/login", Map.of("username", "ana.registrada", "password",
                "senha errada de verdade"))).hasStatus(401);
        assertThat(ana.post("/api/v1/auth/login", Map.of("username", "ana.registrada", "password",
                Accounts.PASSWORD))).hasStatusOk();
        String conversation = ana.json(ana.post("/api/v1/conversations/direct",
                Map.of("username", "bruno.registrado"))).get("id").asString();
        assertThat(ana.post("/api/v1/conversations/" + conversation + "/messages", Map.of(
                "clientMessageId", UUID.randomUUID(), "body", "corpo-secreto-da-mensagem"))).hasStatus(201);
        ana.get(FailingController.PATH);

        assertThat(output.getAll()).doesNotContain(Accounts.PASSWORD)
                .doesNotContain("senha errada de verdade").doesNotContain("ana.registrada")
                .doesNotContain("bruno.registrado").doesNotContain("corpo-secreto-da-mensagem");
    }
}
