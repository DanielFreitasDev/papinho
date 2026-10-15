package io.github.labdev2025cmd.papinho.messaging;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import io.github.labdev2025cmd.papinho.media.MediaView;
import io.github.labdev2025cmd.papinho.media.UploadService;
import io.github.labdev2025cmd.papinho.messaging.MessagingExceptions.NotFoundException;
import io.github.labdev2025cmd.papinho.shared.security.RateLimiter;

/**
 * GIFs (MS-10): busca pelo provedor (desligado por padrão), prévias e o GIF escolhido passam pelo servidor. O
 * cliente só conhece ids; as URLs ficam aqui por alguns minutos e só hosts permitidos são baixados (sem SSRF e
 * sem expor o IP de quem vê ao provedor). O GIF escolhido vira um arquivo de mídia de quem enviou.
 */
@Service
public class GifService {

    private static final Duration RESULT_TTL = Duration.ofMinutes(30);
    private static final int MAX_RESULTS = 30;

    private final GifProvider provider;
    private final GifProperties properties;
    private final UploadService uploads;
    private final RateLimiter rateLimiter;
    private final Clock clock;
    private final Map<String, Cached> results = new ConcurrentHashMap<>();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    GifService(GifProvider provider, GifProperties properties, UploadService uploads, RateLimiter rateLimiter,
            Clock clock) {
        this.provider = provider;
        this.properties = properties;
        this.uploads = uploads;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    public boolean enabled() {
        return provider.enabled();
    }

    public List<GifView> search(UUID me, String query, String locale) {
        requireEnabled();
        rateLimiter.consume(RateLimiter.Policy.GIF_BY_USER, me.toString());
        Instant now = clock.instant();
        results.values().removeIf(cached -> cached.expiresAt().isBefore(now));
        return provider.search(query.strip(), locale, MAX_RESULTS).stream().map(gif -> {
            String key = UUID.randomUUID().toString();
            results.put(key, new Cached(gif, now.plus(RESULT_TTL)));
            return new GifView(key, gif.title(), gif.width(), gif.height());
        }).toList();
    }

    /** Prévia pequena de um resultado recente. */
    public byte[] preview(String key) {
        requireEnabled();
        return download(cached(key).preview());
    }

    /** Baixa o GIF escolhido e o guarda como mídia de {@code me}, pronto para anexar à mensagem. */
    public MediaView attach(UUID me, String key) throws IOException {
        requireEnabled();
        GifProvider.Gif gif = cached(key);
        return uploads.ingest(me, "gif.gif", "image/gif", download(gif.original()));
    }

    private GifProvider.Gif cached(String key) {
        Cached cached = results.get(key);
        if (cached == null || cached.expiresAt().isBefore(clock.instant())) {
            throw new NotFoundException();
        }
        return cached.gif();
    }

    private byte[] download(URI uri) {
        String scheme = uri.getScheme();
        boolean allowedScheme = "https".equals(scheme) || ("http".equals(scheme)
                && "http".equals(properties.baseUrl().getScheme()));
        if (!allowedScheme || uri.getHost() == null || !properties.mediaHosts().contains(uri.getHost())) {
            throw new MessagingExceptions.GifUnavailableException();
        }
        try {
            HttpResponse<InputStream> response = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Papinho").GET().build(), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() != 200) {
                    throw new MessagingExceptions.GifUnavailableException();
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                long total = 0;
                for (int read; (read = body.read(buffer)) != -1;) {
                    total += read;
                    if (total > properties.maxBytes()) {
                        throw new MessagingExceptions.GifUnavailableException();
                    }
                    out.write(buffer, 0, read);
                }
                return out.toByteArray();
            }
        } catch (IOException e) {
            throw new MessagingExceptions.GifUnavailableException();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MessagingExceptions.GifUnavailableException();
        }
    }

    private void requireEnabled() {
        if (!provider.enabled()) {
            throw new MessagingExceptions.GifsDisabledException();
        }
    }

    private record Cached(GifProvider.Gif gif, Instant expiresAt) {
    }

    /** Resultado da busca; a prévia vem de {@code /api/v1/gifs/{id}/preview}. */
    public record GifView(String id, String title, int width, int height) {
    }
}
