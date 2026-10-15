package io.github.labdev2025cmd.papinho.media;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.jspecify.annotations.Nullable;

/**
 * Remove os blocos EXIF e XMP de um WebP (RIFF) sem recodificar a imagem (MD-02), e limpa os bits
 * correspondentes do cabeçalho VP8X. Devolve {@code null} quando o arquivo não é um RIFF/WebP válido.
 */
final class WebpMetadata {

    private static final int EXIF_FLAG = 0x08;
    private static final int XMP_FLAG = 0x04;

    private WebpMetadata() {
    }

    static byte @Nullable [] strip(byte[] data) {
        if (data.length < 12 || !ascii(data, 0, "RIFF") || !ascii(data, 8, "WEBP")) {
            return null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
        out.write(data, 0, 12);
        int offset = 12;
        while (offset + 8 <= data.length) {
            String fourcc = new String(data, offset, 4, StandardCharsets.US_ASCII);
            long size = Integer.toUnsignedLong(littleInt(data, offset + 4));
            long padded = size + (size & 1);
            if (offset + 8 + padded > data.length) {
                return null;
            }
            int length = (int) (8 + padded);
            if (!"EXIF".equals(fourcc) && !"XMP ".equals(fourcc)) {
                byte[] chunk = java.util.Arrays.copyOfRange(data, offset, offset + length);
                if ("VP8X".equals(fourcc) && chunk.length > 8) {
                    chunk[8] = (byte) (chunk[8] & ~(EXIF_FLAG | XMP_FLAG));
                }
                out.write(chunk, 0, chunk.length);
            }
            offset += length;
        }
        byte[] result = out.toByteArray();
        int riffSize = result.length - 8;
        result[4] = (byte) riffSize;
        result[5] = (byte) (riffSize >> 8);
        result[6] = (byte) (riffSize >> 16);
        result[7] = (byte) (riffSize >> 24);
        return result;
    }

    private static boolean ascii(byte[] data, int offset, String text) {
        return new String(data, offset, 4, StandardCharsets.US_ASCII).equals(text);
    }

    private static int littleInt(byte[] data, int offset) {
        return (data[offset] & 0xFF) | (data[offset + 1] & 0xFF) << 8 | (data[offset + 2] & 0xFF) << 16
                | (data[offset + 3] & 0xFF) << 24;
    }
}
