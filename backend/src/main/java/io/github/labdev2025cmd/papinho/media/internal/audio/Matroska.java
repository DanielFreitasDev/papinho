package io.github.labdev2025cmd.papinho.media.internal.audio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * WebM/Matroska (EBML). O MediaRecorder do Chromium e do Firefox grava sem duração no cabeçalho (o Firefox deixa
 * 0,0) e com segmento e clusters de tamanho desconhecido; então a duração vem dos blocos da faixa de áudio: do
 * início do primeiro ao fim do último, menos o atraso do codec (CodecDelay), que é o que o navegador toca. O fim
 * de cada bloco Opus sai do byte TOC do pacote (RFC 6716). A duração declarada só vale quando a leitura dos
 * blocos não chegou ao fim.
 */
final class Matroska {

    private static final long EBML = 0x1A45DFA3L;
    private static final long SEGMENT = 0x18538067L;
    private static final long INFO = 0x1549A966L;
    private static final long TIMECODE_SCALE = 0x2AD7B1L;
    private static final long DURATION = 0x4489L;
    private static final long TRACKS = 0x1654AE6BL;
    private static final long TRACK_ENTRY = 0xAEL;
    private static final long TRACK_NUMBER = 0xD7L;
    private static final long TRACK_TYPE = 0x83L;
    private static final long CODEC_ID = 0x86L;
    private static final long CODEC_DELAY = 0x56AAL;
    private static final long CLUSTER = 0x1F43B675L;
    private static final long TIMECODE = 0xE7L;
    private static final long SIMPLE_BLOCK = 0xA3L;
    private static final long BLOCK_GROUP = 0xA0L;
    private static final long BLOCK = 0xA1L;
    private static final long BLOCK_DURATION = 0x9BL;
    private static final long DISCARD_PADDING = 0x75A2L;
    /** Elementos atravessados: os filhos vêm em sequência, sem precisar do tamanho, que pode ser desconhecido. */
    private static final Set<Long> CONTAINERS = Set.of(SEGMENT, INFO, TRACKS, TRACK_ENTRY, CLUSTER, BLOCK_GROUP);
    private static final long UNKNOWN = -1;
    private static final long INVALID = -2;
    private static final long END = -3;
    private static final int AUDIO_TRACK = 2;
    /** Quadro Opus quando o pacote não diz (laços): 20 ms, o padrão dos navegadores. */
    private static final long OPUS_FRAME_NS = 20_000_000L;

    private Matroska() {
    }

    static @Nullable Double seconds(ByteInput in) throws IOException {
        Blocks blocks = new Blocks();
        long scale = 1_000_000L;
        double declared = 0;
        long cluster = 0;
        List<Track> tracks = new ArrayList<>();
        Track audio = null;
        boolean complete = false;
        byte[] head = new byte[16];
        // Todo arquivo EBML começa pelo cabeçalho EBML; sem ele não é WebM.
        long header = readId(in) == EBML ? readSize(in) : INVALID;
        if (header < 0 || !in.skip(header)) {
            return null;
        }
        while (true) {
            long id = readId(in);
            if (id < 0) {
                complete = id == END;
                break;
            }
            long size = readSize(in);
            if (size == INVALID) {
                break;
            }
            if (CONTAINERS.contains(id)) {
                if (id == TRACK_ENTRY) {
                    tracks.add(new Track());
                } else if (id == CLUSTER) {
                    cluster = 0;
                }
                continue;
            }
            if (size == UNKNOWN) {
                // Só contêineres podem ter tamanho desconhecido; sem saber onde termina, não dá para pular.
                break;
            }
            Track entry = tracks.isEmpty() ? null : tracks.getLast();
            if (id == TIMECODE_SCALE) {
                scale = readUnsigned(in, size, scale);
            } else if (id == DURATION) {
                declared = readFloat(in, size);
            } else if (id == TRACK_NUMBER && entry != null) {
                entry.number = readUnsigned(in, size, 0);
            } else if (id == TRACK_TYPE && entry != null) {
                entry.type = readUnsigned(in, size, 0);
            } else if (id == CODEC_ID && entry != null) {
                entry.codec = readText(in, size);
            } else if (id == CODEC_DELAY && entry != null) {
                entry.delay = readUnsigned(in, size, 0);
            } else if (id == TIMECODE) {
                cluster = readUnsigned(in, size, 0);
            } else if (id == SIMPLE_BLOCK || id == BLOCK) {
                if (audio == null) {
                    audio = audioTrack(tracks);
                }
                int length = (int) Math.min(size, head.length);
                if (!in.readFully(head, 0, length)) {
                    break;
                }
                blocks.add(head, length, cluster, scale, audio);
                if (!in.skip(size - length)) {
                    // Último bloco truncado: o que já foi lido vale.
                    complete = true;
                    break;
                }
            } else if (id == BLOCK_DURATION) {
                blocks.lastDuration(Math.multiplyExact(readUnsigned(in, size, 0), scale));
            } else if (id == DISCARD_PADDING) {
                // Fim do último pacote que não toca (o FFmpeg corta o enchimento do quadro Opus final assim).
                blocks.discard(size == 0 ? 0 : readSigned(in, size));
            } else if (!in.skip(size)) {
                complete = true;
                break;
            }
        }
        // Fora do Opus, o fim do último bloco é estimado: a duração declarada, se houver, é melhor. Leitura
        // interrompida por lixo ou corte no meio de um elemento: sem duração declarada, os blocos lidos são o que
        // o navegador consegue tocar.
        boolean exact = audio == null || audio.number < 0 || "A_OPUS".equals(audio.codec);
        if (blocks.seen() && (declared <= 0 || exact && complete)) {
            long delay = audio == null ? 0 : audio.delay;
            return Math.max(0, blocks.end - blocks.start - delay) / 1e9;
        }
        return declared > 0 ? declared * scale / 1e9 : null;
    }

    /** A primeira faixa de áudio; sem tipo declarado, a única faixa do arquivo. */
    private static Track audioTrack(List<Track> tracks) {
        for (Track track : tracks) {
            if (track.type == AUDIO_TRACK) {
                return track;
            }
        }
        return tracks.size() == 1 ? tracks.getFirst() : new Track();
    }

    /** Id do elemento (1 a 4 bytes, com o marcador); {@link #END} no fim do arquivo. */
    private static long readId(ByteInput in) throws IOException {
        int first = in.read();
        if (first < 0) {
            return END;
        }
        int length = Integer.numberOfLeadingZeros(first) - 23;
        if (first == 0 || length > 4) {
            return INVALID;
        }
        long id = first;
        for (int i = 1; i < length; i++) {
            int next = in.read();
            if (next < 0) {
                return INVALID;
            }
            id = id << 8 | next;
        }
        return id;
    }

    /** Tamanho (1 a 8 bytes, sem o marcador); {@link #UNKNOWN} quando todos os bits são 1. */
    private static long readSize(ByteInput in) throws IOException {
        int first = in.read();
        int length = first <= 0 ? 9 : Integer.numberOfLeadingZeros(first) - 23;
        if (length > 8) {
            return INVALID;
        }
        long value = first & (0xFF >> length);
        boolean allOnes = value == (0xFF >> length);
        for (int i = 1; i < length; i++) {
            int next = in.read();
            if (next < 0) {
                return INVALID;
            }
            value = value << 8 | next;
            allOnes &= next == 0xFF;
        }
        return allOnes ? UNKNOWN : value;
    }

    private static long readUnsigned(ByteInput in, long size, long fallback) throws IOException {
        if (size > 8) {
            in.skip(size);
            return fallback;
        }
        long value = 0;
        for (int i = 0; i < size; i++) {
            int next = in.read();
            if (next < 0) {
                return fallback;
            }
            value = value << 8 | next;
        }
        return value;
    }

    private static long readSigned(ByteInput in, long size) throws IOException {
        long value = readUnsigned(in, size, 0);
        int unused = 64 - 8 * (int) Math.min(size, 8);
        return unused == 0 ? value : value << unused >> unused;
    }

    private static double readFloat(ByteInput in, long size) throws IOException {
        if (size != 4 && size != 8) {
            in.skip(size);
            return 0;
        }
        long bits = readUnsigned(in, size, 0);
        double value = size == 4 ? Float.intBitsToFloat((int) bits) : Double.longBitsToDouble(bits);
        return Double.isFinite(value) ? value : 0;
    }

    private static String readText(ByteInput in, long size) throws IOException {
        if (size > 32) {
            in.skip(size);
            return "";
        }
        byte[] text = new byte[(int) size];
        return in.readFully(text, 0, text.length) ? new String(text, StandardCharsets.US_ASCII) : "";
    }

    /** Faixa declarada em Tracks. */
    private static final class Track {
        long number = -1;
        long type = -1;
        String codec = "";
        long delay;
    }

    /** Início do primeiro bloco e fim do último da faixa de áudio, em nanossegundos. */
    private static final class Blocks {
        long start = Long.MAX_VALUE;
        long end = Long.MIN_VALUE;
        /** O fim antes do bloco mais recente: BlockDuration e DiscardPadding corrigem só o fim dele. */
        long previousEnd = Long.MIN_VALUE;
        /** Início e fim do bloco mais recente. */
        long lastStart = Long.MIN_VALUE;
        long lastEnd = Long.MIN_VALUE;
        /** Distância entre os dois últimos blocos: estimativa do fim quando o codec não diz a duração do pacote. */
        long lastGap;

        boolean seen() {
            return start != Long.MAX_VALUE;
        }

        void add(byte[] head, int length, long cluster, long scale, Track audio) {
            int trackLength = Integer.numberOfLeadingZeros(head[0] & 0xFF) - 23;
            if ((head[0] & 0xFF) == 0 || trackLength + 3 > length) {
                return;
            }
            long track = head[0] & (0xFF >> trackLength);
            for (int i = 1; i < trackLength; i++) {
                track = track << 8 | head[i] & 0xFF;
            }
            if (audio.number >= 0 && track != audio.number) {
                return;
            }
            short relative = (short) ByteInput.u16be(head, trackLength);
            long time = Math.multiplyExact(Math.addExact(cluster, relative), scale);
            int flags = head[trackLength + 2] & 0xFF;
            int data = trackLength + 3;
            long duration;
            if ("A_OPUS".equals(audio.codec) || audio.number < 0) {
                duration = opusDuration(head, length, data, flags);
            } else {
                duration = Math.max(0, lastGap);
            }
            if (lastStart != Long.MIN_VALUE && time > lastStart) {
                lastGap = time - lastStart;
            }
            previousEnd = end;
            lastStart = time;
            lastEnd = Math.addExact(time, duration);
            start = Math.min(start, time);
            end = Math.max(end, lastEnd);
        }

        void lastDuration(long duration) {
            if (seen()) {
                lastEnd = Math.addExact(lastStart, duration);
                end = Math.max(previousEnd, lastEnd);
            }
        }

        void discard(long padding) {
            if (seen() && padding > 0) {
                lastEnd = Math.max(lastStart, lastEnd - padding);
                end = Math.max(previousEnd, lastEnd);
            }
        }

        /** Duração do pacote Opus pelo TOC: tamanho do quadro pela configuração e quantos quadros (RFC 6716 §3.1). */
        private static long opusDuration(byte[] head, int length, int data, int flags) {
            if ((flags & 0x06) != 0) {
                // Laços: vários pacotes no bloco; o TOC de cada um fica depois da tabela de tamanhos.
                return data < length ? ((head[data] & 0xFF) + 1) * OPUS_FRAME_NS : OPUS_FRAME_NS;
            }
            if (data >= length) {
                return OPUS_FRAME_NS;
            }
            int toc = head[data] & 0xFF;
            int config = toc >> 3;
            long frame;
            if (config < 12) {
                frame = new long[] {10_000_000L, 20_000_000L, 40_000_000L, 60_000_000L}[config & 3];
            } else if (config < 16) {
                frame = (config & 1) == 0 ? 10_000_000L : 20_000_000L;
            } else {
                frame = new long[] {2_500_000L, 5_000_000L, 10_000_000L, 20_000_000L}[config & 3];
            }
            int frames = switch (toc & 3) {
                case 0 -> 1;
                case 1, 2 -> 2;
                default -> data + 1 < length ? head[data + 1] & 0x3F : 1;
            };
            return frames * frame;
        }
    }
}
