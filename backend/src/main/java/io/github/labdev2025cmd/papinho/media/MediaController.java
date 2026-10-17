package io.github.labdev2025cmd.papinho.media;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.labdev2025cmd.papinho.shared.security.CurrentUser;

/**
 * Leitura de arquivos (MD-01, MD-02): metadados, conteúdo com suporte a {@code Range} (vídeo e áudio) e
 * miniatura. Tipos que o navegador não deve interpretar são sempre baixados como
 * {@code application/octet-stream}.
 */
@RestController
@RequestMapping("/api/v1/media")
class MediaController {

    /** O conteúdo de um id nunca muda; o cache é só do navegador de quem tem acesso. */
    private static final CacheControl PRIVATE_IMMUTABLE = CacheControl.maxAge(Duration.ofDays(7)).cachePrivate()
            .immutable();

    private final MediaService media;

    MediaController(MediaService media) {
        this.media = media;
    }

    @GetMapping("/{id}")
    MediaView view(@PathVariable UUID id) {
        return media.view(CurrentUser.id(), id);
    }

    @GetMapping("/{id}/content")
    ResponseEntity<Resource> content(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean download,
            @RequestParam(required = false) @Nullable String grant,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) @Nullable String range) throws IOException {
        MediaService.Content content = media.content(CurrentUser.id(), id, false, grant);
        requireSatisfiable(range, content.resource());
        return respond(content, download);
    }

    /**
     * O Spring responde ao Range ilegível com 416, mas manda o arquivo inteiro no corpo: aqui ele sai em Problem
     * Details, sem o conteúdo.
     */
    private static void requireSatisfiable(@Nullable String range, Resource resource) throws IOException {
        if (range == null) {
            return;
        }
        try {
            HttpRange.toResourceRegions(HttpRange.parseRanges(range), resource);
        } catch (IllegalArgumentException invalid) {
            throw new MediaExceptions.RangeNotSatisfiableException(resource.contentLength());
        }
    }

    @GetMapping("/{id}/thumbnail")
    ResponseEntity<Resource> thumbnail(@PathVariable UUID id, @RequestParam(required = false) @Nullable String grant)
            throws IOException {
        return respond(media.content(CurrentUser.id(), id, true, grant), false);
    }

    private static ResponseEntity<Resource> respond(MediaService.Content content, boolean download) {
        boolean inline = content.inline() && !download;
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(content.filename(), StandardCharsets.UTF_8).build();
        MediaType type = content.inline() ? MediaType.parseMediaType(content.contentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok().contentType(type).cacheControl(PRIVATE_IMMUTABLE)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString()).body(content.resource());
    }
}
