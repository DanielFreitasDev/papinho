package io.github.labdev2025cmd.papinho.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import io.github.labdev2025cmd.papinho.support.Accounts;
import io.github.labdev2025cmd.papinho.support.ApiClient;
import io.github.labdev2025cmd.papinho.support.IntegrationTest;
import io.github.labdev2025cmd.papinho.support.TestSupportConfiguration;
import io.github.labdev2025cmd.papinho.support.TestSupportConfiguration.ApiClients;
import tools.jackson.databind.JsonNode;

/** Erros do próprio Spring MVC também saem como Problem Details com os textos dos bundles (SG-01, IN-01). */
@IntegrationTest
class ProblemDetailsIT {

    @Autowired
    private ApiClients clients;

    @Test
    @DisplayName("JSON ilegível, id que não é UUID, método e tipo de mídia errados têm tipo e texto próprios")
    void shouldTranslateFrameworkErrors() {
        ApiClient ana = clients.create();
        Accounts.register(ana, "ana", null);

        expect(ana, ana.perform(MockMvcRequestBuilders.put("/api/v1/conversations/x/draft")
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":")), 400, "bad-request", "Pedido inválido");
        expect(ana, ana.get("/api/v1/conversations/nao-e-uuid"), 400, "bad-request", "Pedido inválido");
        expect(ana, ana.patch("/api/v1/me", null), 405, "method-not-allowed", "Ação não permitida");
        expect(ana, ana.perform(MockMvcRequestBuilders.put("/api/v1/account/username")
                .contentType(MediaType.TEXT_PLAIN).content("ana")), 415, "unsupported-media-type",
                "Formato não aceito");

        MvcTestResult english = ana.perform(MockMvcRequestBuilders.get("/api/v1/conversations/nao-e-uuid")
                .header(HttpHeaders.ACCEPT_LANGUAGE, "en"));
        assertThat(ana.json(english).get("title").asString()).isEqualTo("Invalid request");
    }

    @Test
    @DisplayName("erro inesperado vira 500 internal sem mensagem, classe nem pilha da exceção (SG-01)")
    void shouldHideInternalErrors() {
        ApiClient ana = clients.create();
        Accounts.register(ana, "ana", null);
        MvcTestResult failed = ana.get(TestSupportConfiguration.FailingController.PATH);
        expect(ana, failed, 500, "internal", "Erro interno");
        assertThat(ApiClient.text(failed)).doesNotContain("SegredoInterno").doesNotContain("hunter2")
                .doesNotContain("IllegalStateException").doesNotContain("java.").doesNotContain("org.")
                .doesNotContain("/srv/").doesNotContain("\tat ");
    }

    private static void expect(ApiClient client, MvcTestResult result, int status, String slug, String title) {
        assertThat(result).as(ApiClient.text(result)).hasStatus(status)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        JsonNode problem = client.json(result);
        assertThat(problem.get("type").asString()).isEqualTo("urn:papinho:problem:" + slug);
        assertThat(problem.get("title").asString()).isEqualTo(title);
        assertThat(problem.get("detail").asString()).isNotBlank().doesNotContain("Failed to");
    }
}
