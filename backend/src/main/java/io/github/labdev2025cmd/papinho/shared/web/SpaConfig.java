package io.github.labdev2025cmd.papinho.shared.web;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serve o SPA empacotado em META-INF/resources e devolve index.html para rotas do lado do cliente.
 * O Spring Boot não tem esse fallback nativo (spring-boot#27554), por isso o resolver customizado.
 * Registrar "/**" aqui substitui o handler padrão do Boot (WebMvcAutoConfiguration só o cria se o padrão não existe).
 */
@Configuration(proxyBeanMethods = false)
class SpaConfig implements WebMvcConfigurer {

    private static final String[] LOCATIONS = {"classpath:/META-INF/resources/", "classpath:/static/"};
    private static final String[] ASSET_LOCATIONS = {"classpath:/META-INF/resources/assets/",
            "classpath:/static/assets/"};

    /**
     * Extensões de arquivo estático: um caminho terminado nelas que não existe é 404, não o shell do SPA.
     * Não basta "tem ponto": rotas do app como {@code /add/ana.silva} carregam o {@code @usuario} com ponto.
     */
    private static final Pattern STATIC_FILE = Pattern.compile(
            "(?i).*\\.(js|mjs|css|map|json|webmanifest|txt|xml|html|ico|png|jpe?g|gif|svg|webp|avif|woff2?|ttf|"
                    + "otf|wasm|mp3|mp4|webm|ogg|pdf|zip)$");

    /** Caminhos que nunca recebem o index.html: um 404 de verdade é mais útil que o shell do SPA. */
    private static final List<String> NON_SPA_PREFIXES = List.of("api", "actuator", "ws", "setup", "webauthn");

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Vite gera assets/[nome]-[hash].*: cache imutável de um ano. O caminho dentro do handler já vem sem
        // o prefixo "assets/", por isso as localizações apontam para a subpasta.
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(ASSET_LOCATIONS)
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).immutable());

        // Todo o resto: o arquivo pedido ou index.html. O index nunca é cacheado.
        registry.addResourceHandler("/**")
                .addResourceLocations(LOCATIONS)
                .setCacheControl(CacheControl.noCache())
                .resourceChain(true)
                .addResolver(new SpaFallbackResolver());
    }

    /** Mantém a checagem de segurança do PathResourceResolver e só então aplica o fallback. */
    static final class SpaFallbackResolver extends PathResourceResolver {

        @Override
        protected @Nullable Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource requested = super.getResource(resourcePath, location);
            if (requested != null) {
                return requested;
            }
            // API, actuator, setup e arquivos estáticos inexistentes continuam sendo 404 de verdade.
            boolean notSpaRoute = NON_SPA_PREFIXES.stream()
                    .anyMatch(prefix -> resourcePath.equals(prefix) || resourcePath.startsWith(prefix + "/"))
                    || STATIC_FILE.matcher(resourcePath).matches();
            return notSpaRoute ? null : super.getResource("index.html", location);
        }
    }
}
