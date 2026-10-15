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
}
