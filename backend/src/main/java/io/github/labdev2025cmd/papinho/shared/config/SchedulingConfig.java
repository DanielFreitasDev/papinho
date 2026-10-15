package io.github.labdev2025cmd.papinho.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Liga os jobs agendados (limpeza de limites de tentativa, sessões, mensagens temporárias). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfig {
}
