package io.github.labdev2025cmd.papinho.identity;

import java.util.Set;
import java.util.UUID;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.webauthn.management.JdbcPublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/**
 * Passkeys com o suporte nativo do Spring Security (ADR 0003, ID-04): repositórios JDBC e o carregamento do
 * principal pelo id da conta, que é o nome registrado na passkey.
 */
@Configuration(proxyBeanMethods = false)
class WebAuthnConfig {

    @Bean
    PublicKeyCredentialUserEntityRepository publicKeyCredentialUserEntityRepository(JdbcOperations jdbc) {
        return new JdbcPublicKeyCredentialUserEntityRepository(jdbc);
    }

    @Bean
    UserCredentialRepository userCredentialRepository(JdbcOperations jdbc) {
        return new JdbcUserCredentialRepository(jdbc);
    }

    /**
     * Carrega o principal depois de uma autenticação por passkey. O "username" aqui é o id da conta (o nome do
     * principal na sessão), não o {@code @usuario}; contas desativadas são recusadas.
     */
    @Bean
    UserDetailsService passkeyUserDetailsService(UserAccountRepository users) {
        return id -> {
            UUID userId;
            try {
                userId = UUID.fromString(id);
            } catch (IllegalArgumentException exception) {
                throw new UsernameNotFoundException("Unknown account");
            }
            return users.findById(userId).filter(UserAccount::enabled).map(IdentityService::toPrincipal)
                    .orElseThrow(() -> new UsernameNotFoundException("Unknown account"));
        };
    }

    /**
     * Relying Party das passkeys (prefixo {@code app.identity.webauthn}).
     *
     * @param rpId domínio das passkeys; precisa ser um hostname (WebAuthn não aceita IP), por exemplo
     *     {@code papinho.lan} na LAN ou {@code localhost} no desenvolvimento
     * @param allowedOrigins origens exatas de onde o SPA é servido
     */
    @ConfigurationProperties(prefix = "app.identity.webauthn")
    record WebAuthnProperties(@DefaultValue("localhost") @NotBlank String rpId,
            @DefaultValue({"http://localhost:8080", "http://localhost:5173"}) @NotEmpty Set<String> allowedOrigins) {
    }
}
