package io.github.labdev2025cmd.papinho.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import io.github.labdev2025cmd.papinho.support.IntegrationTest;

/** Cadeia de segurança real contra o PostgreSQL do Testcontainers: deny by default, Problem Details e cabeçalhos. */
@IntegrationTest
class SecurityIT {

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
                .contains("frame-ancestors 'none'").doesNotContain("unsafe-inline").doesNotContain("unsafe-eval");
        assertThat(result).doesNotContainHeader("Strict-Transport-Security");
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
}
