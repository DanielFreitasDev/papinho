package io.github.labdev2025cmd.papinho.media;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.springframework.http.HttpStatus;

import io.github.labdev2025cmd.papinho.shared.error.ApiException;

/**
 * Processamento de imagens enviadas pelos usuários (ID-02, MD-02).
 *
 * <p>Toda imagem é decodificada e recodificada em JPEG: o arquivo final não carrega EXIF, GPS nem qualquer
 * outro metadado do original. As dimensões são lidas antes de decodificar, para recusar "bombas de
 * descompressão" (imagens minúsculas em bytes que ocupam gigabytes na memória).</p>
 */
public final class ImageProcessing {

    /** Limite de pixels decodificados (40 MP cobre fotos de celulares atuais). */
    static final long MAX_PIXELS = 40_000_000L;
    private static final float JPEG_QUALITY = 0.86f;

    private ImageProcessing() {
    }

    /**
     * Recorta o centro em quadrado e redimensiona para {@code size}×{@code size}, em JPEG sem metadados.
     *
     * @throws InvalidImageException se o conteúdo não for PNG, JPEG, GIF ou BMP legível ou for grande demais
     */
    public static byte[] squareJpeg(InputStream input, int size) throws IOException {
        BufferedImage source = decode(input);
        int side = Math.min(source.getWidth(), source.getHeight());
        BufferedImage square = source.getSubimage((source.getWidth() - side) / 2, (source.getHeight() - side) / 2,
                side, side);
        return encodeJpeg(resize(square, Math.min(size, side)));
    }

    /** Redimensiona um JPEG já processado para uma miniatura quadrada. */
    public static byte[] thumbnail(byte[] squareJpeg, int size) throws IOException {
        return squareJpeg(new java.io.ByteArrayInputStream(squareJpeg), size);
    }

    static BufferedImage decode(InputStream input) throws IOException {
        try (ImageInputStream stream = ImageIO.createImageInputStream(input)) {
            if (stream == null) {
                throw new InvalidImageException();
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw new InvalidImageException();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels <= 0 || pixels > MAX_PIXELS) {
                    throw new InvalidImageException();
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new InvalidImageException();
                }
                return image;
            } catch (IOException | RuntimeException exception) {
                if (exception instanceof InvalidImageException invalid) {
                    throw invalid;
                }
                throw new InvalidImageException();
            } finally {
                reader.dispose();
            }
        }
    }

    /** Redução em etapas de no máximo 50% com bicúbica: qualidade próxima do Lanczos sem dependência externa. */
    static BufferedImage resize(BufferedImage source, int size) {
        BufferedImage current = toRgb(source);
        int width = current.getWidth();
        while (width / 2 >= size) {
            width /= 2;
            current = draw(current, width);
        }
        return width == size ? current : draw(current, size);
    }

    private static BufferedImage draw(BufferedImage source, int size) {
        BufferedImage target = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, size, size, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    /** Achata transparência sobre branco (JPEG não tem canal alfa). */
    private static BufferedImage toRgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        try {
            graphics.setColor(java.awt.Color.WHITE);
            graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return rgb;
    }

    static byte[] encodeJpeg(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(buffer)) {
            writer.setOutput(output);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionQuality(JPEG_QUALITY);
            parameters.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
            // IIOImage sem metadados: nada do arquivo original (EXIF, GPS, câmera) é copiado.
            writer.write(null, new IIOImage(image, null, null), parameters);
        } finally {
            writer.dispose();
        }
        return buffer.toByteArray();
    }

    /** Conteúdo enviado não é uma imagem aceita. */
    public static class InvalidImageException extends ApiException {

        public InvalidImageException() {
            super(HttpStatus.UNPROCESSABLE_CONTENT, "image-invalid", "Unsupported or oversized image");
        }
    }
}
