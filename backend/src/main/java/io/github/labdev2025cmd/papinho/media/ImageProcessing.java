package io.github.labdev2025cmd.papinho.media;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
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
        byte[] data = input.readAllBytes();
        // A orientação EXIF é lida direto dos segmentos do JPEG: o leitor de metadados do ImageIO recusa
        // arquivos comuns de celular (APP1 antes do APP0 JFIF).
        int orientation = jpegOrientation(data);
        try (ImageInputStream stream = ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(data))) {
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
                return orient(image, orientation);
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

    /** Decodifica uma imagem (PNG, JPEG, GIF ou BMP) já na orientação certa, recusando bombas de descompressão. */
    public static BufferedImage read(InputStream input) throws IOException {
        return decode(input);
    }

    /**
     * Reduz proporcionalmente até o lado maior caber em {@code maxSide} (não amplia). Mantém o canal alfa
     * quando houver.
     */
    public static BufferedImage fit(BufferedImage source, int maxSide) {
        int longest = Math.max(source.getWidth(), source.getHeight());
        if (longest <= maxSide) {
            return source;
        }
        boolean alpha = source.getColorModel().hasAlpha();
        BufferedImage current = source;
        double scale = (double) maxSide / longest;
        int targetW = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int targetH = Math.max(1, (int) Math.round(source.getHeight() * scale));
        int w = current.getWidth();
        int h = current.getHeight();
        while (w / 2 >= targetW && h / 2 >= targetH) {
            w /= 2;
            h /= 2;
            current = draw(current, w, h, alpha);
        }
        return w == targetW && h == targetH ? current : draw(current, targetW, targetH, alpha);
    }

    /** Recodifica em JPEG sem metadados, achatando transparência sobre branco. */
    public static byte[] jpeg(BufferedImage image) throws IOException {
        return encodeJpeg(toRgb(image));
    }

    /** Recodifica em PNG sem metadados de texto, EXIF ou perfil (preserva transparência). */
    public static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", buffer)) {
            throw new IOException("No PNG writer");
        }
        return buffer.toByteArray();
    }

    /**
     * Orientação EXIF (1 a 8) do segmento APP1 de um JPEG; 1 quando não houver ou não for JPEG. Percorre os
     * segmentos até o início da imagem (SOS) e lê só a tag 0x0112 do IFD0, sem biblioteca extra.
     */
    static int jpegOrientation(byte[] data) {
        if (data.length < 4 || (data[0] & 0xFF) != 0xFF || (data[1] & 0xFF) != 0xD8) {
            return 1;
        }
        int offset = 2;
        while (offset + 4 <= data.length && (data[offset] & 0xFF) == 0xFF) {
            int marker = data[offset + 1] & 0xFF;
            if (marker == 0xDA || marker == 0xD9) {
                return 1;
            }
            int length = ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
            if (length < 2 || offset + 2 + length > data.length) {
                return 1;
            }
            if (marker == 0xE1) {
                int value = orientationFromExif(java.util.Arrays.copyOfRange(data, offset + 4, offset + 2 + length));
                if (value != 1) {
                    return value;
                }
            }
            offset += 2 + length;
        }
        return 1;
    }

    static int orientationFromExif(byte[] data) {
        if (data.length < 14 || data[0] != 'E' || data[1] != 'x' || data[2] != 'i' || data[3] != 'f') {
            return 1;
        }
        int tiff = 6;
        boolean little = data[tiff] == 'I' && data[tiff + 1] == 'I';
        int ifd = tiff + readInt(data, tiff + 4, little);
        if (ifd < tiff || ifd + 2 > data.length) {
            return 1;
        }
        int entries = readShort(data, ifd, little);
        for (int i = 0; i < entries; i++) {
            int entry = ifd + 2 + i * 12;
            if (entry + 12 > data.length) {
                return 1;
            }
            if (readShort(data, entry, little) == 0x0112) {
                int value = readShort(data, entry + 8, little);
                return value >= 1 && value <= 8 ? value : 1;
            }
        }
        return 1;
    }

    private static int readShort(byte[] data, int offset, boolean little) {
        int a = data[offset] & 0xFF;
        int b = data[offset + 1] & 0xFF;
        return little ? a | (b << 8) : (a << 8) | b;
    }

    private static int readInt(byte[] data, int offset, boolean little) {
        int value = 0;
        for (int i = 0; i < 4; i++) {
            int b = data[offset + (little ? 3 - i : i)] & 0xFF;
            value = (value << 8) | b;
        }
        return value;
    }

    /** Aplica a orientação EXIF: 2–4 espelham/giram no plano, 5–8 trocam largura e altura. */
    static BufferedImage orient(BufferedImage image, int orientation) {
        if (orientation <= 1 || orientation > 8) {
            return image;
        }
        int w = image.getWidth();
        int h = image.getHeight();
        boolean swap = orientation >= 5;
        // Matrizes (m00, m10, m01, m11, m02, m12) que levam o pixel do arquivo para a posição de exibição.
        AffineTransform t = switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);
            default -> new AffineTransform(0, -1, 1, 0, 0, w);
        };
        int type = image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage target = new BufferedImage(swap ? h : w, swap ? w : h, type);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.drawImage(image, t, null);
        } finally {
            graphics.dispose();
        }
        return target;
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
        return draw(source, size, size, false);
    }

    private static BufferedImage draw(BufferedImage source, int width, int height, boolean alpha) {
        BufferedImage target = new BufferedImage(width, height,
                alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, width, height, null);
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
