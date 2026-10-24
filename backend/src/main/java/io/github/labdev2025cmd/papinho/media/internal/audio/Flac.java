package io.github.labdev2025cmd.papinho.media.internal.audio;

import java.io.IOException;

import org.jspecify.annotations.Nullable;

/** FLAC nativo: total de amostras e taxa do bloco STREAMINFO, que vem logo depois da assinatura "fLaC". */
final class Flac {

    private static final int STREAMINFO = 34;

    private Flac() {
    }

    static @Nullable Double seconds(ByteInput in) throws IOException {
        in.skipId3v2();
        byte[] data = new byte[STREAMINFO];
        if (!in.readFully(data, 0, 8) || !ByteInput.ascii(data, 0, "fLaC") || (data[4] & 0x7F) != 0
                || ByteInput.u24be(data, 5) < STREAMINFO || !in.readFully(data, 0, STREAMINFO)) {
            return null;
        }
        long rate = (data[10] & 0xFF) << 12 | (data[11] & 0xFF) << 4 | (data[12] & 0xFF) >> 4;
        long samples = (long) (data[13] & 0x0F) << 32 | ByteInput.u32be(data, 14);
        // Zero amostras: o codificador não sabia o total (gravação em fluxo).
        return rate > 0 && samples > 0 ? (double) samples / rate : null;
    }
}
