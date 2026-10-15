package io.github.labdev2025cmd.papinho.media;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.jspecify.annotations.Nullable;

import io.github.labdev2025cmd.papinho.media.internal.ThumbHash;

/**
 * Tratamento de imagens enviadas como mensagem (MD-02): JPEG e PNG são recodificados sem metadados (EXIF, GPS,
 * câmera), com a orientação EXIF aplicada; WebP perde só os blocos EXIF/XMP; GIF segue como veio (não tem
 * EXIF). Todas ganham miniatura JPEG e ThumbHash quando o Java consegue decodificá-las.
 */
final class PhotoPipeline {

    static final int THUMBNAIL_SIDE = 480;
    private static final int THUMBHASH_SIDE = 100;

    private PhotoPipeline() {
    }

    /**
     * @param content conteúdo final (sem metadados) a gravar
     * @param thumbnail miniatura JPEG, nula quando a imagem não pôde ser decodificada
     */
    record Result(byte[] content, @Nullable Integer width, @Nullable Integer height, byte @Nullable [] thumbnail,
            @Nullable String thumbhash) {
    }

    static Result process(byte[] original, String contentType) throws IOException {
        return switch (contentType) {
            case "image/jpeg" -> {
                BufferedImage image = ImageProcessing.read(new ByteArrayInputStream(original));
                yield withPreview(ImageProcessing.jpeg(image), image);
            }
            case "image/png" -> {
                BufferedImage image = ImageProcessing.read(new ByteArrayInputStream(original));
                yield withPreview(ImageProcessing.png(image), image);
            }
            case "image/gif" -> {
                BufferedImage image = ImageProcessing.read(new ByteArrayInputStream(original));
                yield withPreview(original, image);
            }
            case "image/webp" -> {
                byte[] stripped = WebpMetadata.strip(original);
                if (stripped == null) {
                    throw new ImageProcessing.InvalidImageException();
                }
                yield new Result(stripped, null, null, null, null);
            }
            default -> throw new IllegalArgumentException("Not an image: " + contentType);
        };
    }

    private static Result withPreview(byte[] content, BufferedImage image) throws IOException {
        BufferedImage thumbnail = ImageProcessing.fit(image, THUMBNAIL_SIDE);
        String hash = ThumbHash.encode(ImageProcessing.fit(thumbnail, THUMBHASH_SIDE));
        return new Result(content, image.getWidth(), image.getHeight(), ImageProcessing.jpeg(thumbnail), hash);
    }
}
