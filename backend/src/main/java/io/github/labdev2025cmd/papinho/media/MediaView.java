package io.github.labdev2025cmd.papinho.media;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Arquivo enviado, como os outros módulos e o cliente o veem.
 *
 * @param contentType tipo detectado pelo conteúdo (Apache Tika)
 * @param filename nome sanitizado, só para exibição e download
 * @param thumbhash placeholder borrado em Base64 (imagens)
 * @param hasThumbnail há miniatura em {@code /api/v1/media/{id}/thumbnail}
 */
public record MediaView(UUID id, UUID ownerId, MediaKind kind, String contentType, long size, String filename,
        @Nullable Integer width, @Nullable Integer height, @Nullable String thumbhash, boolean hasThumbnail,
        Instant createdAt) {
}
