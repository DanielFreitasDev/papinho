package io.github.labdev2025cmd.papinho.support;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

/** Imagens de teste. */
public final class Images {

    private Images() {
    }

    /**
     * JPEG com um segmento APP1 "Exif" logo após o SOI, carregando o marcador {@code GPS-SECRETO}: simula a foto
     * de celular com localização que o servidor precisa limpar.
     */
    public static byte[] jpegWithExif(int width, int height) {
        try {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setColor(new Color(0x16a34a));
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(Color.WHITE);
            graphics.fillOval(width / 4, height / 4, width / 2, height / 2);
            graphics.dispose();
            ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
            ImageIO.write(image, "jpeg", jpeg);
            byte[] plain = jpeg.toByteArray();

            byte[] payload = "Exif\0\0GPS-SECRETO -3.7319,-38.5267".getBytes(StandardCharsets.ISO_8859_1);
            int length = payload.length + 2;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(plain, 0, 2);
            out.write(0xFF);
            out.write(0xE1);
            out.write((length >> 8) & 0xFF);
            out.write(length & 0xFF);
            out.write(payload);
            out.write(plain, 2, plain.length - 2);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * JPEG {@code width}×{@code height} com metade esquerda vermelha e direita azul e um EXIF válido (TIFF little
     * endian) com a orientação dada e um texto {@code GPS-SECRETO}.
     */
    public static byte[] jpegWithOrientation(int width, int height, int orientation) {
        try {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setColor(Color.RED);
            graphics.fillRect(0, 0, width / 2, height);
            graphics.setColor(Color.BLUE);
            graphics.fillRect(width / 2, 0, width - width / 2, height);
            graphics.dispose();
            ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
            ImageIO.write(image, "jpeg", jpeg);
            byte[] plain = jpeg.toByteArray();

            ByteArrayOutputStream exif = new ByteArrayOutputStream();
            exif.write("Exif\0\0".getBytes(StandardCharsets.ISO_8859_1));
            // Cabeçalho TIFF "II", 42, IFD0 no deslocamento 8; uma entrada: 0x0112 SHORT 1 = orientação.
            exif.write(new byte[] {'I', 'I', 42, 0, 8, 0, 0, 0, 1, 0, 0x12, 0x01, 3, 0, 1, 0, 0, 0,
                (byte) orientation, 0, 0, 0, 0, 0, 0, 0});
            exif.write("GPS-SECRETO".getBytes(StandardCharsets.ISO_8859_1));
            byte[] payload = exif.toByteArray();
            int length = payload.length + 2;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(plain, 0, 2);
            out.write(0xFF);
            out.write(0xE1);
            out.write((length >> 8) & 0xFF);
            out.write(length & 0xFF);
            out.write(payload);
            out.write(plain, 2, plain.length - 2);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** PNG simples com transparência num canto. */
    /** PNG com um bloco {@code tEXt} (metadado de texto, como o de câmeras e editores) logo depois do IHDR. */
    public static byte[] pngWithText(int width, int height, String text) {
        byte[] plain = png(width, height);
        byte[] data = ("Comment\0" + text).getBytes(StandardCharsets.ISO_8859_1);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update("tEXt".getBytes(StandardCharsets.US_ASCII));
        crc.update(data);
        java.nio.ByteBuffer chunk = java.nio.ByteBuffer.allocate(12 + data.length).putInt(data.length)
                .put("tEXt".getBytes(StandardCharsets.US_ASCII)).put(data).putInt((int) crc.getValue());
        int afterHeader = 8 + 25;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(plain, 0, afterHeader);
        out.writeBytes(chunk.array());
        out.write(plain, afterHeader, plain.length - afterHeader);
        return out.toByteArray();
    }

    public static byte[] png(int width, int height) {
        try {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setColor(new Color(0x2563eb));
            graphics.fillRect(0, 0, width, height);
            graphics.setComposite(java.awt.AlphaComposite.Clear);
            graphics.fillRect(0, 0, width / 4, height / 4);
            graphics.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
