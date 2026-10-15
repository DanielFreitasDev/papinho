package io.github.labdev2025cmd.papinho.shared.security;

import java.util.Set;

import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
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
    private static final Set<String> NON_SPA_PREFIXES = Set.of("/api/", "/ws", "/actuator", "/setup", "/login",
            "/logout", "/webauthn");

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

    @Bean
    @Order(1)
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(cookie -> cookie.secure(true).sameSite("Strict").path("/"));
        http.securityMatcher("/api/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/instance").permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfRepository))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .requestCache(cache -> cache.disable())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler));
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
