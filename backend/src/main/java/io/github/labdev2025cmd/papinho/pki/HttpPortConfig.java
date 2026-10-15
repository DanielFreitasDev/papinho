package io.github.labdev2025cmd.papinho.pki;

import java.io.IOException;

import org.apache.catalina.connector.Connector;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Porta HTTP auxiliar quando o TLS é servido pelo próprio Spring com a PKI da LAN (PK-02).
 *
 * <p>Nela só existem {@code /setup/**} (para o aparelho conseguir baixar a CA antes de confiar no HTTPS) e um
 * redirecionamento permanente para HTTPS em todo o resto. O filtro roda antes da segurança, então nenhuma
 * sessão nem API fica exposta em texto claro.</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty("app.pki.enabled")
@ConditionalOnProperty("server.ssl.bundle")
class HttpPortConfig {

    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> httpConnectorCustomizer(PkiProperties properties) {
        return factory -> {
            Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
            connector.setPort(properties.httpPort());
            connector.setScheme("http");
            connector.setSecure(false);
            factory.addAdditionalConnectors(connector);
        };
    }

    @Bean
    FilterRegistrationBean<HttpPortRestrictionFilter> httpPortRestrictionFilter(PkiProperties properties,
            SetupService setup) {
        var registration = new FilterRegistrationBean<>(new HttpPortRestrictionFilter(properties, setup));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }

    /** Recusa tudo na porta HTTP exceto {@code /setup/**}; o resto vira 301 para a mesma URL em HTTPS. */
    static final class HttpPortRestrictionFilter extends OncePerRequestFilter {

        private final PkiProperties properties;
        private final SetupService setup;

        HttpPortRestrictionFilter(PkiProperties properties, SetupService setup) {
            this.properties = properties;
            this.setup = setup;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                FilterChain chain) throws ServletException, IOException {
            if (request.isSecure() || request.getLocalPort() != properties.httpPort()) {
                chain.doFilter(request, response);
                return;
            }
            String path = request.getRequestURI().substring(request.getContextPath().length());
            if (path.equals("/setup") || path.startsWith("/setup/")) {
                chain.doFilter(request, response);
                return;
            }
            // O destino usa o hostname da PKI, nunca o cabeçalho Host: o certificado só vale para ele.
            String host = setup.material().hostname();
            String target = "https://" + properties.httpsAuthority(host) + request.getRequestURI()
                    + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
            response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
            response.setHeader("Location", target);
            response.setHeader("Cache-Control", "no-store");
        }
    }
}
