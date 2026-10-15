package io.github.labdev2025cmd.papinho.shared.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

/**
 * Ponto de extensão para um módulo acrescentar configuração à cadeia de segurança da API sem que
 * {@code shared} dependa dele (por exemplo, as passkeys do módulo de identidade).
 */
@FunctionalInterface
public interface ApiSecurityCustomizer {

    void customize(HttpSecurity http) throws Exception;
}
