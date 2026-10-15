package io.github.labdev2025cmd.papinho.shared.web;

import java.util.List;
import java.util.Locale;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/** Idioma das mensagens ao cliente: pt-BR por padrão, en quando o navegador pede (IN-01). */
@Configuration(proxyBeanMethods = false)
class LocaleConfig {

    static final Locale PT_BR = Locale.of("pt", "BR");

    @Bean
    LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(List.of(PT_BR, Locale.ENGLISH));
        resolver.setDefaultLocale(PT_BR);
        return resolver;
    }
}
