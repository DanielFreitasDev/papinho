package io.github.labdev2025cmd.papinho.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Ouvintes de eventos entre módulos ({@code @ApplicationModuleListener}) rodam fora da requisição, depois do
 * commit, nas threads virtuais do executor do Spring Boot (ADR 0005). O registro de eventos reenvia o que falhar.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
class AsyncConfig {
}
