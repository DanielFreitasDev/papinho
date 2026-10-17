package io.github.labdev2025cmd.papinho.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import io.github.labdev2025cmd.papinho.TestcontainersConfiguration;

/** Com {@code app.security.hsts=true} (VPS atrás de proxy TLS), a resposta HTTPS leva o HSTS (SG-02). */
@SpringBootTest(properties = "app.security.hsts=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class HstsIT {

    @Autowired
    private MockMvcTester mvc;

    @Test
    @DisplayName("HSTS ligado: um ano com subdomínios na resposta segura, nada na resposta em HTTP")
    void shouldSendHstsOverHttps() {
        var secure = mvc.get().uri("https://localhost/api/v1/instance").exchange();
        assertThat(secure).hasStatusOk()
                .hasHeader("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains");
        assertThat(mvc.get().uri("http://localhost/api/v1/instance").exchange())
                .doesNotContainHeader("Strict-Transport-Security");
    }
}
