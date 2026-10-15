package io.github.labdev2025cmd.papinho.identity;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpMessageConverterAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.webauthn4j.util.exception.WebAuthnException;

import io.github.labdev2025cmd.papinho.shared.error.ProblemDetailsWriter;
import io.github.labdev2025cmd.papinho.shared.security.ApiSecurityCustomizer;
import io.github.labdev2025cmd.papinho.shared.web.ClientInfo;
import jakarta.servlet.FilterChain;
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
    private final ProblemDetailsWriter problems;
    private final SecurityContextRepository contextRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    private final UserDetailsService userDetailsService;

    PasskeySecurity(WebAuthnConfig.WebAuthnProperties properties, ProblemDetailsWriter problems,
            SecurityContextRepository contextRepository, CsrfTokenRepository csrfTokenRepository,
            UserDetailsService userDetailsService) {
        this.properties = properties;
        this.problems = problems;
        this.contextRepository = contextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public void customize(HttpSecurity http) throws Exception {
        http.addFilterBefore(new PasskeyErrorFilter(problems), BasicAuthenticationFilter.class);
        http.webAuthn(webauthn -> webauthn
                .rpName("Papinho")
                .rpId(properties.rpId())
                .allowedOrigins(properties.allowedOrigins())
                .disableDefaultRegistrationPage(true)
                .withObjectPostProcessor(new ObjectPostProcessor<WebAuthnAuthenticationFilter>() {
                    @Override
                    public <O extends WebAuthnAuthenticationFilter> O postProcess(O filter) {
                        // O configurer do Spring cria este filtro sem repositório de contexto nem estratégia de
                        // sessão: o login por passkey não ficaria salvo na sessão JDBC. Mesmo tratamento do login
                        // por senha (SessionLogin): troca o id da sessão e gera um token CSRF novo.
                        filter.setSecurityContextRepository(contextRepository);
                        filter.setSessionAuthenticationStrategy(new CompositeSessionAuthenticationStrategy(List.of(
                                new ChangeSessionIdAuthenticationStrategy(),
                                new CsrfAuthenticationStrategy(csrfTokenRepository))));
                        filter.setAuthenticationSuccessHandler(
                                new DescribingSuccessHandler(userDetailsService, contextRepository));
                        return filter;
                    }
                }));
    }

    /**
     * Uma passkey recusada pela verificação (origem errada, assinatura inválida, desafio vencido) sobe como
     * exceção do webauthn4j e viraria 500. Aqui vira 400 em Problem Details, sem detalhes da causa.
     */
    static final class PasskeyErrorFilter extends OncePerRequestFilter {

        private static final Logger log = LoggerFactory.getLogger(PasskeyErrorFilter.class);
        private final ProblemDetailsWriter problems;

        PasskeyErrorFilter(ProblemDetailsWriter problems) {
            this.problems = problems;
        }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            String path = request.getRequestURI();
            return !path.startsWith("/webauthn/") && !path.equals("/login/webauthn");
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                FilterChain chain) throws ServletException, IOException {
            try {
                chain.doFilter(request, response);
            } catch (ServletException | RuntimeException exception) {
                if (!causedByWebAuthn(exception) || response.isCommitted()) {
                    throw exception;
                }
                log.warn("Passkey ceremony rejected reason={}", rootCause(exception).getClass().getSimpleName());
                problems.write(request, response, HttpStatus.BAD_REQUEST, "passkey-invalid");
            }
        }

        private static boolean causedByWebAuthn(Throwable error) {
            for (Throwable current = error; current != null; current = current.getCause()) {
                if (current instanceof WebAuthnException) {
                    return true;
                }
            }
            return false;
        }

        private static Throwable rootCause(Throwable error) {
            Throwable current = error;
            while (current.getCause() != null && current.getCause() != current) {
                current = current.getCause();
            }
            return current;
        }
    }

    /**
     * Depois da passkey: troca o principal ({@code PublicKeyCredentialUserEntity}, no Spring) pelo
     * {@code PapinhoUser} da conta, como no login por senha, grava dispositivo e IP aproximado na sessão (ID-06)
     * e responde em JSON como o handler padrão.
     */
    static final class DescribingSuccessHandler implements AuthenticationSuccessHandler {

        private final AuthenticationSuccessHandler delegate = new HttpMessageConverterAuthenticationSuccessHandler();
        private final UserDetailsService userDetailsService;
        private final SecurityContextRepository contextRepository;
        private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();

        DescribingSuccessHandler(UserDetailsService userDetailsService, SecurityContextRepository contextRepository) {
            this.userDetailsService = userDetailsService;
            this.contextRepository = contextRepository;
        }

        @Override
        public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                Authentication authentication) throws IOException, ServletException {
            UserDetails user = userDetailsService.loadUserByUsername(authentication.getName());
            var replaced = UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
            SecurityContext context = holder.createEmptyContext();
            context.setAuthentication(replaced);
            holder.setContext(context);
            contextRepository.saveContext(context, request, response);
            if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) {
                token.getToken();
            }
            ClientInfo client = ClientInfo.from(request);
            HttpSession session = request.getSession();
            session.setAttribute(SessionService.DEVICE_ATTRIBUTE, client.device());
            session.setAttribute(SessionService.IP_ATTRIBUTE, client.approximateIp());
            delegate.onAuthenticationSuccess(request, response, authentication);
        }
    }
}
