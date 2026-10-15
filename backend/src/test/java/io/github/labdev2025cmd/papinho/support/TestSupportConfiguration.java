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

    /** Cria "navegadores" independentes, cada um com seus cookies e seu IP. */
    @FunctionalInterface
    public interface ApiClients {
        ApiClient create();
    }
}
