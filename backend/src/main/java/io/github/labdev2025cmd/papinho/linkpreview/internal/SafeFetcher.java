package io.github.labdev2025cmd.papinho.linkpreview.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.util.Timeout;
import org.jspecify.annotations.Nullable;

/**
 * Cliente HTTP para conteúdo de terceiros com proteção contra SSRF (MD-04).
 *
 * <ul>
 * <li>só {@code http}/{@code https}, portas padrão, sem usuário na URL;</li>
 * <li>o DNS é resolvido aqui e todo endereço passa pela {@link AddressPolicy}; a conexão usa exatamente os
 * endereços checados (sem nova resolução, o que impede DNS rebinding);</li>
 * <li>redirecionamentos seguidos à mão (no máximo {@value #MAX_REDIRECTS}), cada um checado de novo;</li>
 * <li>tempo de conexão e de resposta curtos e corpo cortado em {@code maxBytes}; sem cookies nem proxy.</li>
 * </ul>
 */
public class SafeFetcher implements AutoCloseable {

    static final int MAX_REDIRECTS = 3;

    private final AddressPolicy policy;
    private final CloseableHttpClient client;

    public SafeFetcher(AddressPolicy policy, String userAgent) {
        this.policy = policy;
        DnsResolver resolver = new GuardedResolver(policy);
        this.client = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create().setDnsResolver(resolver)
                        .setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(Timeout.ofSeconds(4))
                                .setSocketTimeout(Timeout.ofSeconds(6)).build())
                        .setMaxConnTotal(20).setMaxConnPerRoute(4).build())
                .setDefaultRequestConfig(RequestConfig.custom().setResponseTimeout(Timeout.ofSeconds(6))
                        .setRedirectsEnabled(false).build())
                .disableRedirectHandling().disableCookieManagement().disableAuthCaching().disableAutomaticRetries()
                .setUserAgent(userAgent).build();
    }

    /** Resposta obtida (o corpo pode ter sido cortado em {@code maxBytes}, ver {@code truncated}). */
    public record Response(URI uri, @Nullable String contentType, byte[] body, boolean truncated) {
    }

    /** Busca a URL; vazio quando é recusada, falha ou não responde 2xx. */
    public Optional<Response> get(URI start, String accept, int maxBytes) {
        URI current = start;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            if (!acceptable(current)) {
                return Optional.empty();
            }
            HttpGet request = new HttpGet(current);
            request.setHeader("Accept", accept);
            request.setHeader("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.5");
            URI requested = current;
            try {
                Step step = client.execute(request, response -> {
                    int status = response.getCode();
                    if (status >= 300 && status < 400) {
                        Header location = response.getFirstHeader("Location");
                        return location == null ? Step.FAILED : Step.redirect(requested.resolve(location.getValue()));
                    }
                    if (status < 200 || status >= 300) {
                        return Step.FAILED;
                    }
                    HttpEntity entity = response.getEntity();
                    if (entity == null) {
                        return Step.FAILED;
                    }
                    if (entity.getContentLength() > maxBytes * 4L) {
                        return Step.FAILED;
                    }
                    try (InputStream in = entity.getContent()) {
                        byte[] body = readLimited(in, maxBytes + 1);
                        boolean truncated = body.length > maxBytes;
                        return Step.done(new Response(requested, entity.getContentType(),
                                truncated ? Arrays.copyOf(body, maxBytes) : body, truncated));
                    }
                });
                if (step.response != null) {
                    return Optional.of(step.response);
                }
                if (step.next == null) {
                    return Optional.empty();
                }
                current = step.next;
            } catch (IOException | IllegalArgumentException failed) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /** Esquema, porta, usuário e (para IP literal) o próprio endereço. */
    boolean acceptable(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return false;
        }
        if (uri.getRawUserInfo() != null || uri.getHost() == null || uri.getHost().isBlank()) {
            return false;
        }
        int port = uri.getPort();
        if (port != -1 && port != 80 && port != 443 && !policy.allowsPort(port)) {
            return false;
        }
        String host = uri.getHost();
        if (host.startsWith("[") || host.chars().allMatch(c -> Character.isDigit(c) || c == '.')) {
            try {
                return policy.allowed(InetAddress.ofLiteral(host.replace("[", "").replace("]", "")));
            } catch (IllegalArgumentException invalid) {
                return false;
            }
        }
        return true;
    }

    private static byte[] readLimited(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while (out.size() < limit && (read = in.read(buffer, 0, Math.min(buffer.length, limit - out.size()))) > 0) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    @Override
    public void close() throws IOException {
        client.close();
    }

    private record Step(@Nullable URI next, @Nullable Response response) {
        static final Step FAILED = new Step(null, null);

        static Step redirect(URI next) {
            return new Step(next, null);
        }

        static Step done(Response response) {
            return new Step(null, response);
        }
    }

    /** Resolve e recusa a resposta inteira se qualquer endereço for proibido. */
    private static final class GuardedResolver implements DnsResolver {

        private final AddressPolicy policy;

        GuardedResolver(AddressPolicy policy) {
            this.policy = policy;
        }

        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
            InetAddress[] addresses = SystemDefaultDnsResolver.INSTANCE.resolve(host);
            for (InetAddress address : addresses) {
                if (!policy.allowed(address)) {
                    throw new BlockedAddressException(host);
                }
            }
            return addresses;
        }

        @Override
        public String resolveCanonicalHostname(String host) throws UnknownHostException {
            return SystemDefaultDnsResolver.INSTANCE.resolveCanonicalHostname(host);
        }
    }

    /** Endereço proibido pela política; vira "sem prévia". */
    static final class BlockedAddressException extends UnknownHostException {
        BlockedAddressException(String host) {
            super("Blocked address for " + host);
        }
    }
}
