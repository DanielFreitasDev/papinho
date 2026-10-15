package io.github.labdev2025cmd.papinho.transcription;

import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.labdev2025cmd.papinho.shared.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Transcrição (AU-03, AU-05): pedir, consultar, guardar a do navegador, preferências e a tela do admin. */
@RestController
class TranscriptionController {

    private final TranscriptionService transcription;

    TranscriptionController(TranscriptionService transcription) {
        this.transcription = transcription;
    }

    @GetMapping("/api/v1/transcription/options")
    TranscriptionService.Options options() {
        return transcription.options(CurrentUser.id());
    }

    @PutMapping("/api/v1/transcription/preference")
    TranscriptionService.Preference preference(@Valid @RequestBody PreferenceRequest body) {
        return transcription.savePreference(CurrentUser.id(), body.provider(), body.auto());
    }

    @GetMapping("/api/v1/media/{id}/transcript")
    ResponseEntity<TranscriptionService.Transcript> get(@PathVariable UUID id) {
        return transcription.cached(CurrentUser.id(), id).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/api/v1/media/{id}/transcript")
    TranscriptionService.Transcript transcribe(@PathVariable UUID id) {
        return transcription.transcribe(CurrentUser.id(), id);
    }

    @PutMapping("/api/v1/media/{id}/transcript")
    TranscriptionService.Transcript storeFromBrowser(@PathVariable UUID id,
            @Valid @RequestBody BrowserTranscriptRequest body) {
        return transcription.storeFromBrowser(CurrentUser.id(), id, body.text());
    }

    @GetMapping("/api/v1/admin/transcription")
    @PreAuthorize("hasRole('ADMIN')")
    TranscriptionService.AdminView admin() {
        return transcription.adminView();
    }

    @PostMapping("/api/v1/admin/transcription/providers")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    TranscriptionService.AdminProvider create(@Valid @RequestBody CreateProviderRequest body) {
        return transcription.create(body.kind());
    }

    @PutMapping("/api/v1/admin/transcription/providers/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    TranscriptionService.AdminProvider update(@PathVariable UUID id, @Valid @RequestBody UpdateProviderRequest body) {
        return transcription.update(id, body.name(), body.baseUrl(), body.model(), body.hotwords(), body.maxBytes(),
                body.enabled(), body.apiKey());
    }

    @DeleteMapping("/api/v1/admin/transcription/providers/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        transcription.delete(id);
    }

    @PutMapping("/api/v1/admin/transcription/default")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void setDefault(@RequestBody DefaultRequest body) {
        transcription.setDefault(body.providerId());
    }

    @PostMapping("/api/v1/admin/transcription/providers/{id}/health")
    @PreAuthorize("hasRole('ADMIN')")
    Map<String, Boolean> health(@PathVariable UUID id) {
        return Map.of("healthy", transcription.health(id));
    }

    record PreferenceRequest(@NotBlank @Size(max = 40) String provider, boolean auto) {
    }

    record BrowserTranscriptRequest(@NotNull @Size(max = TranscriptionService.MAX_TEXT) String text) {
    }

    record CreateProviderRequest(@NotNull ProviderKind kind) {
    }

    /** @param apiKey nova chave; nula mantém a atual, vazia remove */
    record UpdateProviderRequest(@NotBlank @Size(max = 80) String name, @NotBlank @Size(max = 500) String baseUrl,
            @NotBlank @Size(max = 120) String model, @Size(max = 1000) @Nullable String hotwords,
            @Min(1) @Max(1L << 30) long maxBytes, boolean enabled, @Size(max = 500) @Nullable String apiKey) {
    }

    record DefaultRequest(@Nullable UUID providerId) {
    }
}
