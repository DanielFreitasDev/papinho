package io.github.labdev2025cmd.papinho.linkpreview;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.labdev2025cmd.papinho.linkpreview.internal.AddressPolicy;
import io.github.labdev2025cmd.papinho.linkpreview.internal.SafeFetcher;
import io.github.labdev2025cmd.papinho.media.ImageProcessing;
import io.github.labdev2025cmd.papinho.shared.error.ApiException;
import io.github.labdev2025cmd.papinho.shared.security.RateLimiter;
import jakarta.annotation.PreDestroy;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Prévia de links (MD-04) e incorporação "clique para carregar" (MD-05): YouTube vira
 * {@code youtube-nocookie.com/embed}; Vimeo e Spotify passam pelo oEmbed oficial, numa lista fechada de
 * provedores, e só o {@code src} do iframe (em hosts permitidos) chega ao cliente — nunca HTML de terceiros.
 */
@Service
public class LinkPreviewService {

    static final int MAX_HTML = 512 * 1024;
    static final int MAX_IMAGE = 3 * 1024 * 1024;
    static final int IMAGE_SIDE = 400;
    private static final Pattern YOUTUBE = Pattern.compile(
            "^https?://(?:www\\.|m\\.)?(?:youtube\\.com/(?:watch\\?(?:.*&)?v=|shorts/|embed/|live/)|youtu\\.be/)"
                    + "([A-Za-z0-9_-]{11})");
    private static final Pattern VIMEO = Pattern.compile("^https?://(?:www\\.)?vimeo\\.com/\\d+");
    private static final Pattern SPOTIFY = Pattern
            .compile("^https?://open\\.spotify\\.com/(?:intl-[a-z-]+/)?(?:track|album|playlist|episode|show)/\\w+");

    private final LinkPreviewRepository previews;
    private final LinkPreviewProperties properties;
    private final RateLimiter rateLimiter;
    private final JsonMapper json;
    private final Clock clock;
    private final SafeFetcher fetcher;

    LinkPreviewService(LinkPreviewRepository previews, LinkPreviewProperties properties, RateLimiter rateLimiter,
            JsonMapper json, Clock clock) {
        this.previews = previews;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.json = json;
        this.clock = clock;
        AddressPolicy policy = properties.testAllowedPorts().isEmpty() ? AddressPolicy.PUBLIC_ONLY
                : new AddressPolicy() {
                    @Override
                    public boolean allowed(InetAddress address) {
                        return address.isLoopbackAddress() || AddressPolicy.isPublic(address);
                    }

                    @Override
                    public boolean allowsPort(int port) {
                        return properties.testAllowedPorts().contains(port);
                    }
                };
        this.fetcher = new SafeFetcher(policy, "Papinho/0.1 (link preview)");
    }

    @PreDestroy
    void close() throws IOException {
        fetcher.close();
    }

    /** Prévia do link, do cache ou buscada agora; {@code available} falso quando não há o que mostrar. */
    @Transactional
    public PreviewView preview(java.util.UUID user, String rawUrl) {
        URI uri = normalize(rawUrl);
        String hash = hash(uri.toString());
        Instant now = clock.instant();
        Optional<LinkPreview> cached = previews.findById(hash);
        if (cached.isPresent() && cached.get().fetchedAt().plus(properties.cacheTtl()).isAfter(now)) {
            return cached.get().toView();
        }
        if (!properties.enabled()) {
            return new PreviewView(uri.toString(), false, null, null, null, null, null, null);
        }
        rateLimiter.consume(RateLimiter.Policy.LINK_PREVIEW_BY_USER, user.toString());
        LinkPreview preview = cached.orElseGet(() -> new LinkPreview(hash, uri.toString(), now));
        fetch(uri, preview, now);
        return previews.save(preview).toView();
    }

    @Transactional(readOnly = true)
    public byte[] image(String hash) {
        return previews.findById(hash).map(LinkPreview::image).orElseThrow(NotFoundException::new);
    }

    private void fetch(URI uri, LinkPreview preview, Instant now) {
        String url = uri.toString();
        String embedProvider = null;
        String embedSrc = null;
        String title = null;
        Matcher youtube = YOUTUBE.matcher(url);
        if (youtube.find()) {
            embedProvider = "YOUTUBE";
            embedSrc = "https://www.youtube-nocookie.com/embed/" + youtube.group(1);
        } else if (VIMEO.matcher(url).find()) {
            OEmbed oembed = oembed(properties.vimeoOembed(), url, "player.vimeo.com");
            if (oembed != null) {
                embedProvider = "VIMEO";
                embedSrc = oembed.src();
                title = oembed.title();
            }
        } else if (SPOTIFY.matcher(url).find()) {
            OEmbed oembed = oembed(properties.spotifyOembed(), url, "open.spotify.com");
            if (oembed != null) {
                embedProvider = "SPOTIFY";
                embedSrc = oembed.src();
                title = oembed.title();
            }
        }
        Page page = page(uri);
        if (page != null && page.title() != null) {
            title = page.title();
        }
        boolean available = title != null || embedSrc != null;
        preview.fill(available, cut(title, 300), cut(page == null ? null : page.description(), 1000),
                cut(page == null ? null : page.siteName(), 200), page == null ? null : page.image(), embedProvider,
                embedSrc, now);
    }

    private @Nullable Page page(URI uri) {
        Optional<SafeFetcher.Response> response = fetcher.get(uri, "text/html,application/xhtml+xml", MAX_HTML);
        if (response.isEmpty() || response.get().contentType() == null
                || !response.get().contentType().toLowerCase(Locale.ROOT).contains("html")) {
            return null;
        }
        Document document;
        try {
            document = Jsoup.parse(new ByteArrayInputStream(response.get().body()), null,
                    response.get().uri().toString());
        } catch (IOException unreadable) {
            return null;
        }
        String title = firstNonBlank(meta(document, "og:title"), meta(document, "twitter:title"), document.title());
        String description = firstNonBlank(meta(document, "og:description"), meta(document, "description"),
                meta(document, "twitter:description"));
        String siteName = firstNonBlank(meta(document, "og:site_name"), response.get().uri().getHost());
        byte[] image = null;
        Element imageMeta = document.selectFirst("meta[property=og:image], meta[name=og:image], "
                + "meta[name=twitter:image], meta[property=twitter:image]");
        if (imageMeta != null) {
            String src = imageMeta.absUrl("content");
            image = downloadImage(src);
        }
        return new Page(title, description, siteName, image);
    }

    /** Baixa a imagem da prévia e a recodifica pequena, em JPEG sem metadados. */
    private byte @Nullable [] downloadImage(String src) {
        if (src.isBlank()) {
            return null;
        }
        try {
            Optional<SafeFetcher.Response> response = fetcher.get(new URI(src), "image/*", MAX_IMAGE);
            if (response.isEmpty() || response.get().truncated()) {
                return null;
            }
            BufferedImage decoded = ImageProcessing.read(new ByteArrayInputStream(response.get().body()));
            return ImageProcessing.jpeg(ImageProcessing.fit(decoded, IMAGE_SIDE));
        } catch (URISyntaxException | IOException | RuntimeException ignored) {
            return null;
        }
    }

    private @Nullable OEmbed oembed(String endpoint, String url, String allowedHost) {
        try {
            URI request = new URI(endpoint + "?url=" + URLEncoder.encode(url, StandardCharsets.UTF_8));
            Optional<SafeFetcher.Response> response = fetcher.get(request, "application/json", 64 * 1024);
            if (response.isEmpty()) {
                return null;
            }
            JsonNode node = json.readTree(response.get().body());
            String html = node.path("html").asString("");
            Element iframe = Jsoup.parse(html).selectFirst("iframe[src]");
            if (iframe == null) {
                return null;
            }
            URI src = new URI(iframe.attr("src"));
            if (!"https".equals(src.getScheme()) || !allowedHost.equals(src.getHost())) {
                return null;
            }
            return new OEmbed(src.toString(), node.path("title").asString(null));
        } catch (URISyntaxException | RuntimeException ignored) {
            return null;
        }
    }

    static URI normalize(String raw) {
        String value = raw.strip();
        if (value.length() > 2048) {
            throw new InvalidUrlException();
        }
        try {
            URI uri = new URI(value).normalize();
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https") || uri.getHost() == null) {
                throw new InvalidUrlException();
            }
            return new URI(scheme, uri.getRawAuthority() == null ? null : uri.getAuthority(), uri.getPath(),
                    uri.getQuery(), null);
        } catch (URISyntaxException invalid) {
            throw new InvalidUrlException();
        }
    }

    private static @Nullable String meta(Document document, String key) {
        Element element = document.selectFirst("meta[property=" + key + "], meta[name=" + key + "]");
        return element == null ? null : element.attr("content");
    }

    private static @Nullable String firstNonBlank(@Nullable String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return null;
    }

    private static @Nullable String cut(@Nullable String value, int max) {
        if (value == null) {
            return null;
        }
        String clean = value.replaceAll("\\s+", " ").strip();
        return clean.length() > max ? clean.substring(0, max - 1) + "…" : clean;
    }

    private static String hash(String url) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(url.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record Page(@Nullable String title, @Nullable String description, @Nullable String siteName,
            byte @Nullable [] image) {
    }

    private record OEmbed(String src, @Nullable String title) {
    }

    /**
     * Prévia como o cliente a vê.
     *
     * @param imageUrl miniatura servida pelo próprio Papinho (nunca a URL externa)
     * @param embedProvider YOUTUBE, VIMEO ou SPOTIFY quando há player incorporável
     * @param embedSrc endereço do iframe, carregado só depois do clique
     */
    public record PreviewView(String url, boolean available, @Nullable String title, @Nullable String description,
            @Nullable String siteName, @Nullable String imageUrl, @Nullable String embedProvider,
            @Nullable String embedSrc) {
    }

    static class InvalidUrlException extends ApiException {
        InvalidUrlException() {
            super(HttpStatus.BAD_REQUEST, "link-invalid", "Only http(s) URLs are accepted");
        }
    }

    static class NotFoundException extends ApiException {
        NotFoundException() {
            super(HttpStatus.NOT_FOUND, "not-found", "Link preview image not found");
        }
    }
}
