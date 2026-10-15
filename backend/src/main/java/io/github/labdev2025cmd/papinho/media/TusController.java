package io.github.labdev2025cmd.papinho.media;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import io.github.labdev2025cmd.papinho.shared.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servidor tus 1.0 (ADR 0009) com as extensões {@code creation}, {@code termination} e {@code expiration}. O
 * corpo do {@code PATCH} é lido como fluxo, sem passar pelo multipart do Spring. Ao completar, a resposta traz
 * {@code Papinho-Media-Id} (o mesmo id do upload).
 */
@RestController
@RequestMapping("/api/v1/uploads")
class TusController {

    static final String VERSION = "1.0.0";
    static final String OFFSET_CONTENT_TYPE = "application/offset+octet-stream";

    private final UploadService uploads;

    TusController(UploadService uploads) {
        this.uploads = uploads;
    }

    @RequestMapping(method = RequestMethod.OPTIONS)
    ResponseEntity<Void> options() {
        return ResponseEntity.noContent().header("Tus-Resumable", VERSION).header("Tus-Version", VERSION)
                .header("Tus-Max-Size", Long.toString(MediaProperties.MAX_FILE_SIZE))
                .header("Tus-Extension", "creation,termination,expiration").build();
    }

    @PostMapping
    ResponseEntity<Void> create(@RequestHeader(value = "Tus-Resumable", required = false) @Nullable String version,
            @RequestHeader(value = "Upload-Length", required = false) @Nullable String length,
            @RequestHeader(value = "Upload-Metadata", required = false) @Nullable String metadata,
            HttpServletResponse response) {
        requireVersion(version, response);
        if (length == null || !length.matches("\\d{1,19}")) {
            throw new MediaExceptions.InvalidUploadException("Upload-Length");
        }
        Map<String, String> meta = parseMetadata(metadata);
        UploadService.UploadStatus status = uploads.create(CurrentUser.id(), Long.parseLong(length),
                meta.get("filename"), meta.get("filetype"));
        return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.LOCATION, "/api/v1/uploads/" + status.id())
                .header("Tus-Resumable", VERSION).header("Upload-Expires", expires(status)).build();
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.HEAD)
    ResponseEntity<Void> head(@PathVariable UUID id,
            @RequestHeader(value = "Tus-Resumable", required = false) @Nullable String version,
            HttpServletResponse response) throws IOException {
        requireVersion(version, response);
        UploadService.UploadStatus status = uploads.status(CurrentUser.id(), id);
        return ResponseEntity.ok().header("Tus-Resumable", VERSION)
                .header("Upload-Offset", Long.toString(status.offset()))
                .header("Upload-Length", Long.toString(status.length())).header("Upload-Expires", expires(status))
                .header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    @PatchMapping("/{id}")
    ResponseEntity<Void> patch(@PathVariable UUID id,
            @RequestHeader(value = "Tus-Resumable", required = false) @Nullable String version,
            @RequestHeader(value = "Upload-Offset", required = false) @Nullable String offset,
            @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) @Nullable String contentType,
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        requireVersion(version, response);
        if (contentType == null || !contentType.startsWith(OFFSET_CONTENT_TYPE)) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).header("Tus-Resumable", VERSION).build();
        }
        if (offset == null || !offset.matches("\\d{1,19}")) {
            throw new MediaExceptions.InvalidUploadException("Upload-Offset");
        }
        UploadService.UploadStatus status = uploads.append(CurrentUser.id(), id, Long.parseLong(offset),
                request.getInputStream());
        ResponseEntity.HeadersBuilder<?> builder = ResponseEntity.noContent().header("Tus-Resumable", VERSION)
                .header("Upload-Offset", Long.toString(status.offset())).header("Upload-Expires", expires(status));
        if (status.media() != null) {
            builder.header("Papinho-Media-Id", status.media().id().toString());
        }
        return builder.build();
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id,
            @RequestHeader(value = "Tus-Resumable", required = false) @Nullable String version,
            HttpServletResponse response) throws IOException {
        requireVersion(version, response);
        uploads.cancel(CurrentUser.id(), id);
        return ResponseEntity.noContent().header("Tus-Resumable", VERSION).build();
    }

    private static void requireVersion(@Nullable String version, HttpServletResponse response) {
        response.setHeader("Tus-Resumable", VERSION);
        if (!VERSION.equals(version)) {
            response.setHeader("Tus-Version", VERSION);
            throw new MediaExceptions.TusVersionException();
        }
    }

    private static String expires(UploadService.UploadStatus status) {
        return java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
                .format(status.expiresAt().atZone(java.time.ZoneOffset.UTC));
    }

    /** {@code Upload-Metadata}: pares "chave base64" separados por vírgula. */
    static Map<String, String> parseMetadata(@Nullable String header) {
        Map<String, String> result = new HashMap<>();
        if (header == null || header.isBlank()) {
            return result;
        }
        for (String pair : header.split(",")) {
            String[] parts = pair.strip().split(" ", 2);
            if (parts[0].isEmpty()) {
                continue;
            }
            try {
                String value = parts.length == 2
                        ? new String(Base64.getDecoder().decode(parts[1].strip()), StandardCharsets.UTF_8) : "";
                result.put(parts[0], value);
            } catch (IllegalArgumentException invalid) {
                throw new MediaExceptions.InvalidUploadException("Upload-Metadata");
            }
        }
        return result;
    }
}
