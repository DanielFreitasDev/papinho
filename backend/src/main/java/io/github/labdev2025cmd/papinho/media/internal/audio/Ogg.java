package io.github.labdev2025cmd.papinho.media.internal.audio;

import java.io.IOException;

import org.jspecify.annotations.Nullable;

/**
 * Ogg (RFC 3533) com Opus, Vorbis, FLAC ou Speex: a posição de grânulo da última página do fluxo de áudio é o
 * número de amostras tocadas. No Opus ela conta a 48 kHz e inclui o pre-skip do codificador, que o navegador
 * descarta (RFC 7845). Arquivos encadeados (um fluxo depois do outro) somam as partes.
 */
final class Ogg {

    private static final int PAGE_HEADER = 27;
    /** O pacote de identificação de qualquer codec aceito cabe nisso. */
    private static final int IDENTIFICATION = 64;

    private Ogg() {
    }

    static @Nullable Double seconds(ByteInput in) throws IOException {
        byte[] header = new byte[PAGE_HEADER];
        byte[] lacing = new byte[255];
        byte[] packet = new byte[IDENTIFICATION];
        Codec codec = null;
        long serial = -1;
        long granule = -1;
        boolean ended = false;
        double previous = 0;
        while (in.readFully(header, 0, PAGE_HEADER)) {
            if (!ByteInput.ascii(header, 0, "OggS") || header[4] != 0) {
                break;
            }
            int segments = header[26] & 0xFF;
            if (!in.readFully(lacing, 0, segments)) {
                break;
            }
            long payload = 0;
            for (int i = 0; i < segments; i++) {
                payload += lacing[i] & 0xFF;
            }
            boolean first = (header[5] & 0x02) != 0;
            boolean last = (header[5] & 0x04) != 0;
            long pageSerial = ByteInput.u32le(header, 14);
            long pageGranule = ByteInput.u64le(header, 6);
            // Um fluxo novo só conta se não houver áudio ou se o anterior já terminou (encadeado); fluxos
            // simultâneos (vídeo, esqueleto, segunda faixa) ficam de fora.
            if (first && (codec == null || ended)) {
                int length = (int) Math.min(payload, IDENTIFICATION);
                if (!in.readFully(packet, 0, length)) {
                    break;
                }
                Codec found = Codec.of(packet, length);
                if (found != null) {
                    if (codec != null) {
                        previous += codec.seconds(granule);
                    }
                    codec = found;
                    serial = pageSerial;
                    granule = -1;
                    ended = false;
                }
                payload -= length;
            } else if (codec != null && pageSerial == serial) {
                // -1: nenhum pacote termina nesta página.
                if (pageGranule != -1) {
                    granule = pageGranule;
                }
                ended |= last;
            }
            if (!in.skip(payload)) {
                break;
            }
        }
        return codec == null ? null : previous + codec.seconds(granule);
    }

    /** Taxa em que o grânulo conta e amostras a descartar no início. */
    private record Codec(long rate, long preSkip) {

        static @Nullable Codec of(byte[] packet, int length) {
            Codec codec = null;
            if (length >= 12 && ByteInput.ascii(packet, 0, "OpusHead")) {
                codec = new Codec(48_000, ByteInput.u16le(packet, 10));
            } else if (length >= 16 && packet[0] == 1 && ByteInput.ascii(packet, 1, "vorbis")) {
                codec = new Codec(ByteInput.u32le(packet, 12), 0);
            } else if (length >= 30 && packet[0] == 0x7F && ByteInput.ascii(packet, 1, "FLAC")) {
                // Cabeçalho de mapeamento (13 bytes) seguido do STREAMINFO: taxa em 20 bits.
                long rate = (packet[27] & 0xFF) << 12 | (packet[28] & 0xFF) << 4 | (packet[29] & 0xFF) >> 4;
                codec = new Codec(rate, 0);
            } else if (length >= 40 && ByteInput.ascii(packet, 0, "Speex   ")) {
                codec = new Codec(ByteInput.u32le(packet, 36), 0);
            }
            return codec != null && codec.rate > 0 ? codec : null;
        }

        double seconds(long granule) {
            return granule <= preSkip ? 0 : (double) (granule - preSkip) / rate;
        }
    }
}
