package io.github.labdev2025cmd.papinho.media.internal.audio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.jspecify.annotations.Nullable;

/**
 * WAV (RIFF): tamanho do bloco {@code data} dividido pelos bytes por segundo do {@code fmt }. Formatos
 * comprimidos trazem o número de amostras no bloco {@code fact}, que é mais exato.
 */
final class Wave {

    private static final int PCM = 1;
    private static final int FLOAT = 3;
    private static final int EXTENSIBLE = 0xFFFE;

    private Wave() {
    }

    static @Nullable Double seconds(ByteInput in) throws IOException {
        byte[] chunk = new byte[16];
        if (!in.readFully(chunk, 0, 12) || !ByteInput.ascii(chunk, 0, "RIFF") || !ByteInput.ascii(chunk, 8, "WAVE")) {
            return null;
        }
        int format = 0;
        long rate = 0;
        long byteRate = 0;
        long samples = -1;
        while (in.readFully(chunk, 0, 8)) {
            String id = new String(chunk, 0, 4, StandardCharsets.ISO_8859_1);
            long size = ByteInput.u32le(chunk, 4);
            long read = 0;
            if (id.equals("fmt ") && size >= 16) {
                if (!in.readFully(chunk, 0, 16)) {
                    return null;
                }
                format = ByteInput.u16le(chunk, 0);
                rate = ByteInput.u32le(chunk, 4);
                byteRate = ByteInput.u32le(chunk, 8);
                read = 16;
            } else if (id.equals("fact") && size >= 4) {
                if (!in.readFully(chunk, 0, 4)) {
                    return null;
                }
                samples = ByteInput.u32le(chunk, 0);
                read = 4;
            } else if (id.equals("data")) {
                if (samples >= 0 && rate > 0 && format != PCM && format != FLOAT && format != EXTENSIBLE) {
                    return (double) samples / rate;
                }
                return byteRate > 0 && size > 0 ? (double) size / byteRate : null;
            }
            // Blocos de tamanho ímpar têm um byte de enchimento.
            if (!in.skip(size - read + (size & 1))) {
                break;
            }
        }
        return null;
    }
}
