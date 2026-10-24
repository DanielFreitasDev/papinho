package io.github.labdev2025cmd.papinho.media.internal.audio;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Leitura sequencial do arquivo para os leitores de contêiner: lê em blocos pequenos e pula o resto, sem nunca
 * alocar pelo tamanho que o arquivo declara (um tamanho forjado só faz a leitura chegar ao fim antes).
 */
final class ByteInput {

    private final BufferedInputStream in;

    ByteInput(InputStream in) {
        this.in = new BufferedInputStream(in, 64 * 1024);
    }

    /** Próximo byte (0 a 255) ou -1 no fim do arquivo. */
    int read() throws IOException {
        return in.read();
    }

    /** Lê exatamente {@code length} bytes; {@code false} se o arquivo acabou antes. */
    boolean readFully(byte[] buffer, int offset, int length) throws IOException {
        return in.readNBytes(buffer, offset, length) == length;
    }

    /**
     * Pula {@code count} bytes; {@code false} se o arquivo acabou antes. Num arquivo local o salto pode passar do
     * fim sem erro: nesse caso o fim aparece na leitura seguinte.
     */
    boolean skip(long count) throws IOException {
        long remaining = count;
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped <= 0) {
                if (in.read() < 0) {
                    return false;
                }
                skipped = 1;
            }
            remaining -= skipped;
        }
        return true;
    }

    /** Marca a posição para voltar com {@link #reset()} depois de ler no máximo {@code limit} bytes. */
    void mark(int limit) {
        in.mark(limit);
    }

    void reset() throws IOException {
        in.reset();
    }

    /** Pula a etiqueta ID3v2 do início (MP3, AAC e FLAC podem trazê-la); sem etiqueta, nada é consumido. */
    void skipId3v2() throws IOException {
        byte[] header = new byte[10];
        mark(header.length);
        if (!readFully(header, 0, header.length) || !ascii(header, 0, "ID3")) {
            reset();
            return;
        }
        long size = (header[6] & 0x7F) << 21 | (header[7] & 0x7F) << 14 | (header[8] & 0x7F) << 7 | header[9] & 0x7F;
        boolean footer = (header[5] & 0x10) != 0;
        skip(size + (footer ? 10 : 0));
    }

    static boolean ascii(byte[] data, int offset, String text) {
        return offset + text.length() <= data.length
                && new String(data, offset, text.length(), StandardCharsets.ISO_8859_1).equals(text);
    }

    static int u16be(byte[] data, int offset) {
        return (data[offset] & 0xFF) << 8 | data[offset + 1] & 0xFF;
    }

    static int u24be(byte[] data, int offset) {
        return (data[offset] & 0xFF) << 16 | u16be(data, offset + 1);
    }

    static long u32be(byte[] data, int offset) {
        return (long) (data[offset] & 0xFF) << 24 | u24be(data, offset + 1);
    }

    static long u64be(byte[] data, int offset) {
        return u32be(data, offset) << 32 | u32be(data, offset + 4);
    }

    static int u16le(byte[] data, int offset) {
        return data[offset] & 0xFF | (data[offset + 1] & 0xFF) << 8;
    }

    static long u32le(byte[] data, int offset) {
        return u16le(data, offset) | (long) u16le(data, offset + 2) << 16;
    }

    static long u64le(byte[] data, int offset) {
        return u32le(data, offset) | u32le(data, offset + 4) << 32;
    }
}
