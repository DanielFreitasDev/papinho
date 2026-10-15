package io.github.labdev2025cmd.papinho.identity;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.password.CompromisedPasswordChecker;
import org.springframework.security.web.authentication.password.HaveIBeenPwnedRestApiPasswordChecker;

/** Beans do módulo de identidade. */
@Configuration(proxyBeanMethods = false)
class IdentityConfig {

    /** Consulta ao Have I Been Pwned; só é chamada quando o admin liga a checagem. */
    @Bean
    CompromisedPasswordChecker compromisedPasswordChecker() {
        return new HaveIBeenPwnedRestApiPasswordChecker();
    }
}
