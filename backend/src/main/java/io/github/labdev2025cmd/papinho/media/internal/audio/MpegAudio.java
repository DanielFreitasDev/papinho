package io.github.labdev2025cmd.papinho.media.internal.audio;

import java.io.IOException;

import org.jspecify.annotations.Nullable;

/**
 * MP3 (MPEG-1/2/2.5, camadas I a III). O cabeçalho Xing/Info ou VBRI do primeiro quadro diz quantos quadros há
 * (com o atraso e o enchimento do codificador da etiqueta LAME, descontados como o navegador faz); sem ele, os
 * quadros são contados um a um até o fim do áudio.
 */
final class MpegAudio {

    /** Onde procurar o primeiro quadro depois da etiqueta ID3v2. */
    private static final int SYNC_SEARCH = 64 * 1024;
    /** Um quadro tem no máximo 2.881 bytes; o seguinte é conferido antes de aceitar o primeiro. */
    private static final int MAX_FRAME = 2_881;
    private static final int[][] BITRATES = {
        {32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448},
        {32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384},
        {32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320},
        {32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256},
        {8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160},
    };
    private static final int[][] RATES = {{11_025, 12_000, 8_000}, {0, 0, 0}, {22_050, 24_000, 16_000},
        {44_100, 48_000, 32_000}};

    private MpegAudio() {
    }

    static @Nullable Double seconds(ByteInput in) throws IOException {
        in.skipId3v2();
        Frame first = findFirst(in);
        if (first == null) {
            return null;
        }
        byte[] data = new byte[Math.min(first.length - 4, 200)];
        if (!in.readFully(data, 0, data.length)) {
            return null;
        }
        Double tagged = fromTag(first, data);
        if (tagged != null) {
            return tagged;
        }
        long samples = first.samples;
        boolean more = in.skip(first.length - 4L - data.length);
        byte[] header = new byte[4];
        while (more && in.readFully(header, 0, 4)) {
            Frame frame = Frame.of(header);
            if (frame == null || !frame.sameStream(first)) {
                // Etiqueta ID3v1/APE no fim ou lixo: acabou o áudio.
                break;
            }
            samples += frame.samples;
            more = in.skip(frame.length - 4L);
        }
        return (double) samples / first.rate;
    }

    /** Primeiro cabeçalho válido seguido de outro do mesmo fluxo (evita falso sincronismo em lixo). */
    private static @Nullable Frame findFirst(ByteInput in) throws IOException {
        byte[] header = new byte[4];
        for (int searched = 0; searched < SYNC_SEARCH; searched++) {
            in.mark(MAX_FRAME + 8);
            if (!in.readFully(header, 0, 4)) {
                return null;
            }
            Frame frame = Frame.of(header);
            if (frame != null) {
                byte[] next = new byte[4];
                boolean confirmed = in.skip(frame.length - 4L) && in.readFully(next, 0, 4)
                        && Frame.of(next) instanceof Frame following && following.sameStream(frame);
                in.reset();
                if (confirmed || frame.length >= 4 && singleFrame(in, frame)) {
                    in.skip(4);
                    return frame;
                }
            } else {
                in.reset();
            }
            in.skip(1);
        }
        return null;
    }

    /** Arquivo de um quadro só: depois dele vem o fim. */
    private static boolean singleFrame(ByteInput in, Frame frame) throws IOException {
        in.mark(MAX_FRAME + 8);
        boolean end = in.skip(frame.length) && in.read() < 0;
        in.reset();
        return end;
    }

    /** Quadros pelo cabeçalho Xing/Info ou VBRI; nulo se não houver. */
    private static @Nullable Double fromTag(Frame first, byte[] data) {
        int xing = first.sideInfo;
        if (ByteInput.ascii(data, xing, "Xing") || ByteInput.ascii(data, xing, "Info")) {
            if (xing + 8 > data.length) {
                return null;
            }
            long flags = ByteInput.u32be(data, xing + 4);
            int offset = xing + 8;
            if ((flags & 0x01) == 0 || offset + 4 > data.length) {
                return null;
            }
            long frames = ByteInput.u32be(data, offset);
            offset += 4 + ((flags & 0x02) != 0 ? 4 : 0) + ((flags & 0x04) != 0 ? 100 : 0)
                    + ((flags & 0x08) != 0 ? 4 : 0);
            long samples = frames * first.samples;
            // Etiqueta LAME (também escrita pelo FFmpeg): atraso e enchimento em 12 bits cada, 21 bytes adiante.
            if (offset + 24 <= data.length && Character.isLetter(data[offset] & 0xFF)) {
                int delay = (data[offset + 21] & 0xFF) << 4 | (data[offset + 22] & 0xFF) >> 4;
                int padding = (data[offset + 22] & 0x0F) << 8 | data[offset + 23] & 0xFF;
                if (delay + padding < samples) {
                    samples -= delay + padding;
                }
            }
            return (double) samples / first.rate;
        }
        if (ByteInput.ascii(data, 32, "VBRI") && data.length >= 50) {
            return (double) (ByteInput.u32be(data, 46) * first.samples) / first.rate;
        }
        return null;
    }

    /**
     * Cabeçalho de quadro.
     *
     * @param sideInfo onde termina a informação lateral, contada depois do cabeçalho (lá fica o Xing)
     */
    private record Frame(int version, int layer, int rate, int length, int samples, int sideInfo) {

        static @Nullable Frame of(byte[] header) {
            if ((header[0] & 0xFF) != 0xFF || (header[1] & 0xE0) != 0xE0) {
                return null;
            }
            int version = header[1] >> 3 & 3;
            int layer = header[1] >> 1 & 3;
            int bitrateIndex = (header[2] & 0xFF) >> 4;
            int rateIndex = header[2] >> 2 & 3;
            if (version == 1 || layer == 0 || bitrateIndex == 0 || bitrateIndex == 15 || rateIndex == 3) {
                return null;
            }
            boolean mpeg1 = version == 3;
            int table = mpeg1 ? 3 - layer : layer == 3 ? 3 : 4;
            int bitrate = BITRATES[table][bitrateIndex - 1] * 1000;
            int rate = RATES[version][rateIndex];
            int padding = header[2] >> 1 & 1;
            boolean mono = (header[3] & 0xFF) >> 6 == 3;
            int length;
            int samples;
            if (layer == 3) {
                length = (12 * bitrate / rate + padding) * 4;
                samples = 384;
            } else if (layer == 2 || mpeg1) {
                length = 144 * bitrate / rate + padding;
                samples = 1152;
            } else {
                length = 72 * bitrate / rate + padding;
                samples = 576;
            }
            int sideInfo = mpeg1 ? (mono ? 17 : 32) : (mono ? 9 : 17);
            return new Frame(version, layer, rate, length, samples, sideInfo);
        }

        boolean sameStream(Frame other) {
            return version == other.version && layer == other.layer && rate == other.rate;
        }
    }
}
