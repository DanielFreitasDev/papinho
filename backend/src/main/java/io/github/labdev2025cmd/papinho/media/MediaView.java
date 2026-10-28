package io.github.labdev2025cmd.papinho.media;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Arquivo enviado, como os outros módulos e o cliente o veem.
 *
 * @param contentType tipo detectado pelo conteúdo (Apache Tika)
 * @param filename nome sanitizado, só para exibição e download
 * @param width largura da imagem; no PDF, a da prévia da primeira página (a miniatura)
 * @param height altura da imagem; no PDF, a da prévia, que mostra só o topo de página mais comprida que 1:2
 * @param thumbhash placeholder borrado em Base64 (imagens e prévia do PDF)
 * @param hasThumbnail há miniatura em {@code /api/v1/media/{id}/thumbnail}
 * @param durationMs duração do áudio lida do arquivo (AU-02); nula quando o arquivo não diz
 * @param pageCount número de páginas do PDF (MD-01); nulo quando não é PDF ou ele não abre
 */
public record MediaView(UUID id, UUID ownerId, MediaKind kind, String contentType, long size, String filename,
        @Nullable Integer width, @Nullable Integer height, @Nullable String thumbhash, boolean hasThumbnail,
        @Nullable Long durationMs, @Nullable Integer pageCount, Instant createdAt) {
}
