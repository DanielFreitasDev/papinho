package io.github.labdev2025cmd.papinho.shared.security;

import java.util.List;
import java.util.Set;

import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy;
import org.springframework.security.web.header.writers.CrossOriginResourcePolicyHeaderWriter.CrossOriginResourcePolicy;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Cadeias de segurança da aplicação (ADR 0003: sessão por cookie, nunca JWT).
 *
 * <p>Ordem das cadeias: API, Actuator, página {@code /setup} da PKI, shell do SPA e, por último, uma cadeia
 * {@code denyAll} que recusa qualquer requisição não prevista pelas anteriores (deny by default).</p>
 *
 * <p>Todas aplicam os mesmos cabeçalhos: CSP estrita, {@code nosniff}, {@code X-Frame-Options: DENY},
 * COOP/CORP {@code same-origin}, {@code Referrer-Policy: same-origin} e HSTS quando ligado.</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

    /** Prefixos que nunca são rotas do SPA; um GET neles não cai na cadeia pública do shell. */
    private static final Set<String> NON_SPA_PREFIXES = Set.of("/api/", "/ws", "/actuator", "/setup",
            "/login/webauthn", "/webauthn/");

    private final SecurityProperties properties;
    private final ProblemDetailsSecurityHandler problemHandler;

    SecurityConfig(SecurityProperties properties, ProblemDetailsSecurityHandler problemHandler) {
        this.properties = properties;
        this.problemHandler = problemHandler;
    }

    /** Senhas e códigos com Argon2id (parâmetros recomendados pelo Spring Security 5.8+). */
    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    /** Cookie {@code XSRF-TOKEN} legível pelo SPA, {@code Secure} e {@code SameSite=Strict}. */
    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.secure(true).sameSite("Strict").path("/"));
        return repository;
    }

    /** Contexto de segurança guardado na sessão HTTP (Spring Session JDBC); usado também pelo login. */
    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    @Order(1)
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, CsrfTokenRepository csrfTokenRepository,
            SecurityContextRepository securityContextRepository, List<ApiSecurityCustomizer> customizers)
            throws Exception {
        // /webauthn/** e /login/webauthn são os endpoints de passkey do Spring Security (ID-04); /ws é o
        // handshake do WebSocket (ADR 0004), que exige a mesma sessão da API.
        http.securityMatcher("/api/**", "/webauthn/**", "/login/webauthn", "/ws")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/webauthn/authenticate/options", "/login/webauthn")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/instance", "/api/v1/invites/status",
                                "/api/v1/usernames/availability", "/api/v1/auth/session").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/register",
                                "/api/v1/auth/recover", "/api/v1/auth/password-reset",
                                "/api/v1/auth/password-reset/confirm", "/api/v1/auth/email/verify").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .logout(logout -> logout.logoutUrl("/api/v1/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .requestCache(cache -> cache.disable())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler));
        for (ApiSecurityCustomizer customizer : customizers) {
            customizer.customize(http);
        }
        applyHeaders(http);
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(EndpointRequest.toAnyEndpoint())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(EndpointRequest.to("health", "info")).permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .csrf(Customizer.withDefaults())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .requestCache(cache -> cache.disable())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler));
        applyHeaders(http);
        return http.build();
    }

    /** Página de instalação da CA da LAN: pública e somente leitura (PK-03, PK-04). */
    @Bean
    @Order(3)
    SecurityFilterChain setupSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/setup", "/setup/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/setup", "/setup/**").permitAll()
                        .anyRequest().denyAll())
                .csrf(Customizer.withDefaults())
                .requestCache(cache -> cache.disable())
                .sessionManagement(session -> session.disable())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler));
        applyHeaders(http);
        return http.build();
    }

    /** Shell do SPA e seus assets: GET/HEAD públicos fora dos prefixos de API; a proteção fica na API. */
    @Bean
    @Order(4)
    SecurityFilterChain spaSecurityFilterChain(HttpSecurity http) throws Exception {
        RequestMatcher spaRequest = request -> {
            String method = request.getMethod();
            if (!HttpMethod.GET.matches(method) && !HttpMethod.HEAD.matches(method)) {
                return false;
            }
            String path = request.getRequestURI().substring(request.getContextPath().length());
            return NON_SPA_PREFIXES.stream().noneMatch(path::startsWith);
        };
        http.securityMatcher(spaRequest)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(Customizer.withDefaults())
                .requestCache(cache -> cache.disable())
                .sessionManagement(session -> session.disable());
        applyHeaders(http);
        return http.build();
    }

    /** Última cadeia: o que nenhuma cadeia anterior reconheceu é recusado. */
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    SecurityFilterChain denyAllSecurityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth.anyRequest().denyAll())
                .csrf(Customizer.withDefaults())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .requestCache(cache -> cache.disable())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler));
        applyHeaders(http);
        return http.build();
    }

    private void applyHeaders(HttpSecurity http) throws Exception {
        http.headers(headers -> {
            headers.contentSecurityPolicy(csp -> csp.policyDirectives(properties.contentSecurityPolicy()))
                    .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                    .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.SAME_ORIGIN))
                    .permissionsPolicyHeader(permissions -> permissions.policy(properties.permissionsPolicy()))
                    .crossOriginOpenerPolicy(coop -> coop.policy(CrossOriginOpenerPolicy.SAME_ORIGIN))
                    .crossOriginResourcePolicy(corp -> corp.policy(CrossOriginResourcePolicy.SAME_ORIGIN));
            if (properties.hsts()) {
                headers.httpStrictTransportSecurity(hsts -> hsts.maxAgeInSeconds(31_536_000).includeSubDomains(true));
            } else {
                headers.httpStrictTransportSecurity(HeadersConfigurer.HstsConfig::disable);
            }
        });
    }
}
