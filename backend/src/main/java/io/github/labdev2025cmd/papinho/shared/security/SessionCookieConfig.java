package io.github.labdev2025cmd.papinho.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Cookie da sessão (ADR 0003): {@code __Host-SESSION}, {@code HttpOnly}, {@code Secure}, {@code SameSite=Lax},
 * {@code Path=/} e sem {@code Domain}. Declarado aqui em vez de {@code server.servlet.session.cookie.*}
 * porque a autoconfiguração ignora essas propriedades fora do servidor embarcado, e a política do cookie de
 * sessão não pode depender do modo de implantação.
 */
@Configuration(proxyBeanMethods = false)
class SessionCookieConfig {

    static final String COOKIE_NAME = "__Host-SESSION";

    @Bean
    CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName(COOKIE_NAME);
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(true);
        serializer.setSameSite("Lax");
        return serializer;
    }
}
