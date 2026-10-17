package io.github.labdev2025cmd.papinho.linkpreview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import io.github.labdev2025cmd.papinho.shared.security.PapinhoUser;

/**
 * Teste slice da camada web (QA-01): só o controller, o tratamento de erros e o MVC, com o serviço simulado e sem
 * os filtros de segurança (provados nos testes de integração).
 */
@WebMvcTest(LinkPreviewController.class)
@AutoConfigureMockMvc(addFilters = false)
class LinkPreviewControllerTest {

    private static final PapinhoUser ANA = new PapinhoUser(UUID.randomUUID(), "ana", false, true, null);

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private LinkPreviewService previews;

    @Test
    @DisplayName("devolve a prévia do serviço para o usuário autenticado")
    void shouldReturnPreview() {
        when(previews.preview(eq(ANA.id()), any())).thenReturn(new LinkPreviewService.PreviewView(
                "https://exemplo.org/", true, "Título", null, "Exemplo", null, null, null));
        // Sem os filtros de segurança, o usuário vai direto no contexto da thread que o MockMvc usa.
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(ANA, null, ANA.getAuthorities()));
        try {
            assertThat(mvc.get().uri("/api/v1/link-previews?url=https://exemplo.org/")).hasStatusOk().bodyJson()
                    .extractingPath("$.title").isEqualTo("Título");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("hash fora do formato e url longa demais viram 400 em Problem Details sem chegar ao serviço")
    void shouldValidateParameters() {
        assertThat(mvc.get().uri("/api/v1/link-previews/nao-e-hash/image")).hasStatus(400)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(mvc.get().uri("/api/v1/link-previews?url=https://exemplo.org/" + "a".repeat(2100)))
                .hasStatus(400).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        verifyNoInteractions(previews);
    }
}
