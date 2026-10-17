package io.github.labdev2025cmd.papinho.transcription;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.labdev2025cmd.papinho.media.MediaKind;
import io.github.labdev2025cmd.papinho.media.MediaService;
import io.github.labdev2025cmd.papinho.media.MediaView;
import io.github.labdev2025cmd.papinho.shared.crypto.SecretCipher;
import io.github.labdev2025cmd.papinho.shared.error.ApiException;
import io.github.labdev2025cmd.papinho.shared.id.Uuids;
import io.github.labdev2025cmd.papinho.shared.security.RateLimiter;
import io.github.labdev2025cmd.papinho.transcription.internal.OpenAiCompatibleClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Transcrição sob demanda com cache (AU-03) e provedores de servidor (AU-05). Quem pode ouvir o áudio pode
 * pedir a transcrição; o resultado fica guardado e serve a todos os membros da conversa.
 */
@Service
public class TranscriptionService {

    /** Preferência "usar o padrão da instância". */
    public static final String DEFAULT = "DEFAULT";
    /** Transcrição no navegador (AU-04): o cliente transcreve e só envia o texto. */
    public static final String BROWSER = "BROWSER";
    static final int MAX_TEXT = 100_000;

    private static final Logger log = LoggerFactory.getLogger(TranscriptionService.class);

    private final TranscriptionProviderRepository providers;
    private final JdbcTemplate jdbc;
    private final MediaService media;
    private final SecretCipher cipher;
    private final RateLimiter rateLimiter;
    private final Clock clock;
    private final OpenAiCompatibleClient client;

    TranscriptionService(TranscriptionProviderRepository providers, JdbcTemplate jdbc, MediaService media,
            SecretCipher cipher, RateLimiter rateLimiter, Clock clock, JsonMapper json,
            ObjectProvider<BuildProperties> build) {
        this.providers = providers;
        this.jdbc = jdbc;
        this.media = media;
        this.cipher = cipher;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
        BuildProperties properties = build.getIfAvailable();
        this.client = new OpenAiCompatibleClient(json, properties == null ? "dev" : properties.getVersion());
    }

    // ---- usuário ----

    @Transactional(readOnly = true)
    public Options options(UUID user) {
        UUID defaultId = defaultProviderId();
        List<ProviderOption> enabled = providers.findAllByOrderByCreatedAt().stream()
                .filter(TranscriptionProvider::enabled).map(p -> new ProviderOption(p.id(), p.name())).toList();
        return new Options(defaultId == null ? BROWSER : defaultId.toString(), enabled, preference(user));
    }

    @Transactional
    public Preference savePreference(UUID user, String provider, boolean auto) {
        if (!DEFAULT.equals(provider) && !BROWSER.equals(provider)) {
            UUID id = parse(provider);
            providers.findById(id).filter(TranscriptionProvider::enabled).orElseThrow(NotFoundException::new);
        }
        jdbc.update("""
                insert into transcription_preferences (user_id, provider, auto) values (?, ?, ?)
                on conflict (user_id) do update set provider = excluded.provider, auto = excluded.auto
                """, user, provider, auto);
        return new Preference(provider, auto);
    }

    @Transactional(readOnly = true)
    public Optional<Transcript> cached(UUID viewer, UUID mediaId) {
        requireAudio(viewer, mediaId);
        return find(mediaId);
    }

    /**
     * Transcreve no servidor com o provedor escolhido pelo usuário (ou o padrão). Devolve o cache quando já
     * existir; com o provedor "no navegador", responde 409 para o cliente transcrever.
     */
    public Transcript transcribe(UUID viewer, UUID mediaId) {
        MediaView audio = requireAudio(viewer, mediaId);
        Optional<Transcript> existing = find(mediaId);
        if (existing.isPresent()) {
            return existing.get();
        }
        TranscriptionProvider provider = effectiveProvider(viewer);
        if (provider == null) {
            throw new BrowserProviderException();
        }
        if (audio.size() > provider.maxBytes()) {
            throw new TooLargeException(provider.maxBytes() / (1024 * 1024));
        }
        rateLimiter.consume(RateLimiter.Policy.TRANSCRIBE_BY_USER, viewer.toString());
        String key = provider.apiKeyEncrypted() == null ? null : cipher.decrypt(provider.apiKeyEncrypted());
        String text;
        try {
            MediaService.Content content = media.content(viewer, mediaId, false);
            try (InputStream in = content.resource().getInputStream()) {
                text = client.transcribe(provider.baseUrl(), key, provider.model(),
                        provider.kind() == ProviderKind.WHISPER ? provider.hotwords() : null, content.filename(),
                        content.contentType(), in);
            }
        } catch (IOException failed) {
            // Só o provedor e o motivo técnico; nunca a chave nem o conteúdo.
            log.warn("Transcription failed provider={} reason={}", provider.name(), failed.getMessage());
            throw new FailedException();
        }
        return store(mediaId, text, provider.kind().name());
    }

    /** Guarda o texto transcrito no navegador (AU-04); o primeiro que chegar vale. */
    @Transactional
    public Transcript storeFromBrowser(UUID viewer, UUID mediaId, String text) {
        requireAudio(viewer, mediaId);
        return store(mediaId, text, BROWSER);
    }

    // ---- admin ----

    @Transactional(readOnly = true)
    public AdminView adminView() {
        UUID defaultId = defaultProviderId();
        return new AdminView(defaultId, providers.findAllByOrderByCreatedAt().stream().map(TranscriptionService::toAdmin)
                .toList());
    }

    @Transactional
    public AdminProvider create(ProviderKind kind) {
        return toAdmin(providers.save(new TranscriptionProvider(Uuids.v7(), kind, clock.instant())));
    }

    @Transactional
    public AdminProvider update(UUID id, String name, String baseUrl, String model, @Nullable String hotwords,
            long maxBytes, boolean enabled, @Nullable String apiKey) {
        TranscriptionProvider provider = providers.findById(id).orElseThrow(NotFoundException::new);
        URI uri = URI.create(baseUrl.strip());
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https") || uri.getHost() == null) {
            throw new InvalidProviderException();
        }
        provider.update(name.strip(), baseUrl.strip(), model.strip(),
                hotwords == null || hotwords.isBlank() ? null : hotwords.strip(), maxBytes, enabled);
        if (apiKey != null) {
            provider.apiKeyEncrypted(apiKey.isBlank() ? null : cipher.encrypt(apiKey.strip()));
        }
        return toAdmin(provider);
    }

    @Transactional
    public void delete(UUID id) {
        providers.deleteById(id);
    }

    @Transactional
    public void setDefault(@Nullable UUID providerId) {
        if (providerId != null) {
            providers.findById(providerId).orElseThrow(NotFoundException::new);
        }
        jdbc.update("update transcription_settings set default_provider_id = ? where id = 1", providerId);
    }

    @Transactional(readOnly = true)
    public boolean health(UUID id) {
        TranscriptionProvider provider = providers.findById(id).orElseThrow(NotFoundException::new);
        return client.healthy(provider.baseUrl());
    }

    // ---- interno ----

    private @Nullable TranscriptionProvider effectiveProvider(UUID user) {
        String choice = preference(user).provider();
        UUID id;
        if (BROWSER.equals(choice)) {
            return null;
        } else if (DEFAULT.equals(choice)) {
            id = defaultProviderId();
        } else {
            id = parse(choice);
        }
        if (id == null) {
            return null;
        }
        return providers.findById(id).filter(TranscriptionProvider::enabled).orElse(null);
    }

    private Preference preference(UUID user) {
        List<Preference> found = jdbc.query("select provider, auto from transcription_preferences where user_id = ?",
                (rs, i) -> new Preference(rs.getString("provider"), rs.getBoolean("auto")), user);
        return found.isEmpty() ? new Preference(DEFAULT, false) : found.getFirst();
    }

    private @Nullable UUID defaultProviderId() {
        return jdbc.queryForObject("select default_provider_id from transcription_settings where id = 1", UUID.class);
    }

    private MediaView requireAudio(UUID viewer, UUID mediaId) {
        MediaView view = media.view(viewer, mediaId);
        if (view.kind() != MediaKind.AUDIO) {
            throw new NotAudioException();
        }
        return view;
    }

    private Optional<Transcript> find(UUID mediaId) {
        return jdbc.query("select text, source, created_at from transcripts where media_id = ?",
                (rs, i) -> new Transcript(mediaId, rs.getString("text"), rs.getString("source")), mediaId).stream()
                .findFirst();
    }

    private Transcript store(UUID mediaId, String text, String source) {
        String clean = text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) : text;
        jdbc.update("insert into transcripts (media_id, text, source, created_at) values (?, ?, ?, ?) "
                + "on conflict (media_id) do nothing", mediaId, clean, source,
                clock.instant().atOffset(java.time.ZoneOffset.UTC));
        return find(mediaId).orElseThrow();
    }

    private static UUID parse(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException invalid) {
            throw new NotFoundException();
        }
    }

    private static AdminProvider toAdmin(TranscriptionProvider p) {
        return new AdminProvider(p.id(), p.kind(), p.name(), p.baseUrl(), p.model(), p.hotwords(), p.maxBytes(),
                p.enabled(), p.apiKeyEncrypted() != null);
    }

    /** User-Agent usado com os provedores (para teste e diagnóstico). */
    public String userAgent() {
        return client.userAgent();
    }

    public record Transcript(UUID mediaId, String text, String source) {
    }

    public record Preference(String provider, boolean auto) {
    }

    public record ProviderOption(UUID id, String name) {
    }

    /** @param instanceDefault id do provedor padrão ou {@code BROWSER} */
    public record Options(String instanceDefault, List<ProviderOption> providers, Preference preference) {
    }

    /** Provedor para o admin: a chave nunca volta, só {@code hasKey}. */
    public record AdminProvider(UUID id, ProviderKind kind, String name, String baseUrl, String model,
            @Nullable String hotwords, long maxBytes, boolean enabled, boolean hasKey) {
    }

    public record AdminView(@Nullable UUID defaultProviderId, List<AdminProvider> providers) {
    }

    static class NotFoundException extends ApiException {
        NotFoundException() {
            super(HttpStatus.NOT_FOUND, "not-found", "Transcript or provider not found");
        }
    }

    static class NotAudioException extends ApiException {
        NotAudioException() {
            super(HttpStatus.BAD_REQUEST, "transcription-not-audio", "Only audio can be transcribed");
        }
    }

    static class BrowserProviderException extends ApiException {
        BrowserProviderException() {
            super(HttpStatus.CONFLICT, "transcription-browser", Map.of("provider", BROWSER),
                    "Transcription happens in the browser");
        }
    }

    static class TooLargeException extends ApiException {
        TooLargeException(long megabytes) {
            super(HttpStatus.CONTENT_TOO_LARGE, "transcription-too-large", "Audio larger than provider limit",
                    megabytes);
        }
    }

    static class FailedException extends ApiException {
        FailedException() {
            super(HttpStatus.BAD_GATEWAY, "transcription-failed", "Transcription provider failed");
        }
    }

    static class InvalidProviderException extends ApiException {
        InvalidProviderException() {
            super(HttpStatus.BAD_REQUEST, "transcription-provider-invalid", "Provider base URL must be http(s)");
        }
    }
}
