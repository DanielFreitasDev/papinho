package io.github.labdev2025cmd.papinho.identity;

import java.io.IOException;

import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpMessageConverterAuthenticationSuccessHandler;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationFilter;
import org.springframework.stereotype.Component;

import io.github.labdev2025cmd.papinho.shared.security.ApiSecurityCustomizer;
import io.github.labdev2025cmd.papinho.shared.web.ClientInfo;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Liga as passkeys na cadeia da API: opções de registro e de login, registro e login por passkey, com o
 * RP ID e as origens configurados. A página de registro padrão do Spring é desligada (o SPA tem a sua).
 * Depois do login por passkey, a sessão recebe a descrição do dispositivo, como no login por senha.
 */
@Component
class PasskeySecurity implements ApiSecurityCustomizer {

    private final WebAuthnConfig.WebAuthnProperties properties;

    PasskeySecurity(WebAuthnConfig.WebAuthnProperties properties) {
        this.properties = properties;
    }

    @Override
    public void customize(HttpSecurity http) throws Exception {
        http.webAuthn(webauthn -> webauthn
                .rpName("Papinho")
                .rpId(properties.rpId())
                .allowedOrigins(properties.allowedOrigins())
                .disableDefaultRegistrationPage(true)
                .withObjectPostProcessor(new ObjectPostProcessor<WebAuthnAuthenticationFilter>() {
                    @Override
                    public <O extends WebAuthnAuthenticationFilter> O postProcess(O filter) {
                        filter.setAuthenticationSuccessHandler(new DescribingSuccessHandler());
                        return filter;
                    }
                }));
    }

    /** Grava dispositivo e IP aproximado na sessão (ID-06) e responde em JSON como o handler padrão. */
    static final class DescribingSuccessHandler implements AuthenticationSuccessHandler {

        private final AuthenticationSuccessHandler delegate = new HttpMessageConverterAuthenticationSuccessHandler();

        @Override
        public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                Authentication authentication) throws IOException, ServletException {
            ClientInfo client = ClientInfo.from(request);
            HttpSession session = request.getSession();
            session.setAttribute(SessionService.DEVICE_ATTRIBUTE, client.device());
            session.setAttribute(SessionService.IP_ATTRIBUTE, client.approximateIp());
            delegate.onAuthenticationSuccess(request, response, authentication);
        }
    }
}
