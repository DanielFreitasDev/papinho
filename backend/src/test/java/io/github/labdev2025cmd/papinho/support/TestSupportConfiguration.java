package io.github.labdev2025cmd.papinho.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import tools.jackson.databind.json.JsonMapper;

/** Fábrica de {@link ApiClient} para os testes de integração. */
@TestConfiguration(proxyBeanMethods = false)
public class TestSupportConfiguration {

    @Bean
    ApiClients apiClients(MockMvcTester mvc, JsonMapper json) {
        return () -> new ApiClient(mvc, json);
    }

    /** Nenhum teste faz push de verdade: o que seria enviado fica guardado para conferir. */
    @Bean
    @org.springframework.context.annotation.Primary
    RecordingPushGateway recordingPushGateway() {
        return new RecordingPushGateway();
    }

    /** Antivírus que o teste pode derrubar; aceita tudo enquanto está no ar (o padrão sem ClamAV). */
    @Bean
    @org.springframework.context.annotation.Primary
    SwitchableScanner switchableScanner() {
        return new SwitchableScanner();
    }

    /**
     * Rota só dos testes (o scan de componentes a acha no classpath de teste) que falha de um jeito inesperado,
     * para provar o 500 em Problem Details (SG-01).
     */
    @org.springframework.web.bind.annotation.RestController
    public static class FailingController {

        public static final String PATH = "/api/v1/test-only/failure";
        public static final String SECRET = "org.example.SegredoInterno: senha=hunter2 em /srv/papinho";

        @org.springframework.web.bind.annotation.GetMapping(PATH)
        String fail() {
            throw new IllegalStateException(SECRET);
        }
    }

    /** Cria "navegadores" independentes, cada um com seus cookies e seu IP. */
    @FunctionalInterface
    public interface ApiClients {
        ApiClient create();
    }
}
