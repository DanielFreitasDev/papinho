package io.github.labdev2025cmd.papinho.media;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;

/**
 * Tira os metadados de JPEG e PNG em fluxo, sem decodificar a imagem (MD-02): serve às fotos grandes demais para o
 * {@link PhotoPipeline}, que recodifica em memória. O que vier depois do fim da imagem (EOI ou IEND) é descartado.
 */
final class PhotoMetadata {

    private static final byte[] ICC_PROFILE = "ICC_PROFILE\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    /** Blocos de texto, EXIF e data do PNG; os de cor e de imagem ficam. */
    private static final Set<String> PNG_METADATA = Set.of("eXIf", "tEXt", "zTXt", "iTXt", "tIME");

    private PhotoMetadata() {
    }

    /**
     * Copia o JPEG sem os segmentos APP1 a APP15 (EXIF, XMP, IPTC, dados do fabricante) nem comentários. Ficam o
     * APP0 (JFIF), o perfil de cor ICC (APP2) e o APP14 da Adobe, que mudam as cores se faltarem.
     */
    static void stripJpeg(InputStream source, OutputStream out) throws IOException {
        DataInputStream in = new DataInputStream(source);
        if (in.readUnsignedByte() != 0xFF || in.readUnsignedByte() != 0xD8) {
            throw new ImageProcessing.InvalidImageException();
        }
        out.write(new byte[] {(byte) 0xFF, (byte) 0xD8});
        int marker = nextMarker(in);
        while (true) {
            if (marker == 0xD9) {
                out.write(new byte[] {(byte) 0xFF, (byte) 0xD9});
                return;
            }
            if (marker == 0x01 || marker >= 0xD0 && marker <= 0xD7) {
                out.write(new byte[] {(byte) 0xFF, (byte) marker});
                marker = nextMarker(in);
                continue;
            }
            int length = in.readUnsignedShort();
            if (length < 2) {
                throw new ImageProcessing.InvalidImageException();
            }
            byte[] payload = in.readNBytes(length - 2);
            if (payload.length != length - 2) {
                throw new EOFException();
            }
            if (keepsJpegSegment(marker, payload)) {
                out.write(new byte[] {(byte) 0xFF, (byte) marker, (byte) (length >> 8), (byte) length});
                out.write(payload);
            }
            marker = marker == 0xDA ? copyScan(in, out) : nextMarker(in);
        }
    }

    private static boolean keepsJpegSegment(int marker, byte[] payload) {
        if (marker == 0xFE) {
            return false;
        }
        if (marker == 0xE2) {
            return payload.length >= ICC_PROFILE.length
                    && Arrays.equals(payload, 0, ICC_PROFILE.length, ICC_PROFILE, 0, ICC_PROFILE.length);
        }
        return marker < 0xE1 || marker > 0xEF || marker == 0xEE;
    }

    /** Copia os dados comprimidos depois de um SOS e devolve o marcador seguinte. */
    private static int copyScan(DataInputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int used = 0;
        while (true) {
            int value = in.readUnsignedByte();
            if (value != 0xFF) {
                buffer[used++] = (byte) value;
            } else {
                int next = in.readUnsignedByte();
                while (next == 0xFF) {
                    next = in.readUnsignedByte();
                }
                if (next != 0x00 && (next < 0xD0 || next > 0xD7)) {
                    out.write(buffer, 0, used);
                    return next;
                }
                buffer[used++] = (byte) 0xFF;
                if (used == buffer.length) {
                    out.write(buffer, 0, used);
                    used = 0;
                }
                buffer[used++] = (byte) next;
            }
            if (used >= buffer.length - 1) {
                out.write(buffer, 0, used);
                used = 0;
            }
        }
    }

    private static int nextMarker(DataInputStream in) throws IOException {
        if (in.readUnsignedByte() != 0xFF) {
            throw new ImageProcessing.InvalidImageException();
        }
        int marker = in.readUnsignedByte();
        while (marker == 0xFF) {
            marker = in.readUnsignedByte();
        }
        return marker;
    }

    /** Copia o PNG sem os blocos de texto, EXIF e data. */
    static void stripPng(InputStream source, OutputStream out) throws IOException {
        DataInputStream in = new DataInputStream(source);
        byte[] signature = in.readNBytes(PNG_SIGNATURE.length);
        if (!Arrays.equals(signature, PNG_SIGNATURE)) {
            throw new ImageProcessing.InvalidImageException();
        }
        out.write(signature);
        while (true) {
            int length = in.readInt();
            byte[] type = in.readNBytes(4);
            if (length < 0 || type.length != 4) {
                throw new ImageProcessing.InvalidImageException();
            }
            String name = new String(type, StandardCharsets.US_ASCII);
            boolean keep = !PNG_METADATA.contains(name);
            if (keep) {
                out.write(new byte[] {(byte) (length >> 24), (byte) (length >> 16), (byte) (length >> 8),
                        (byte) length});
                out.write(type);
            }
            long remaining = length + 4L;
            byte[] buffer = new byte[8192];
            while (remaining > 0) {
                int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) {
                    throw new EOFException();
                }
                if (keep) {
                    out.write(buffer, 0, read);
                }
                remaining -= read;
            }
            if (name.equals("IEND")) {
                return;
            }
        }
    }
}
