package io.github.labdev2025cmd.papinho.media.internal.audio;

import java.io.IOException;

import org.jspecify.annotations.Nullable;

/** AAC cru em quadros ADTS (arquivo .aac): cada quadro traz 1.024 amostras por bloco; conta-se até o fim. */
final class Adts {

    private static final int[] RATES = {96_000, 88_200, 64_000, 48_000, 44_100, 32_000, 24_000, 22_050, 16_000,
        12_000, 11_025, 8_000, 7_350};

    private Adts() {
    }

    static @Nullable Double seconds(ByteInput in) throws IOException {
        in.skipId3v2();
        byte[] header = new byte[7];
        long samples = 0;
        int rate = 0;
        while (in.readFully(header, 0, 7)) {
            // Sincronismo de 12 bits e camada 00.
            if ((header[0] & 0xFF) != 0xFF || (header[1] & 0xF6) != 0xF0) {
                break;
            }
            int index = header[2] >> 2 & 0x0F;
            int length = (header[3] & 0x03) << 11 | (header[4] & 0xFF) << 3 | (header[5] & 0xFF) >> 5;
            if (index >= RATES.length || rate != 0 && RATES[index] != rate || length < 7) {
                break;
            }
            rate = RATES[index];
            samples += 1024L * ((header[6] & 0x03) + 1);
            if (!in.skip(length - 7L)) {
                break;
            }
        }
        return rate == 0 ? null : (double) samples / rate;
    }
}
