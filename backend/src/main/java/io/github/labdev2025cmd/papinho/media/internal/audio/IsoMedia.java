package io.github.labdev2025cmd.papinho.media.internal.audio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * MP4/M4A (ISO BMFF, ISO/IEC 14496-12). A duração vem do {@code mvhd} (já com a lista de edição) ou do
 * {@code mdhd} da faixa de som. O MP4 fragmentado, como o do MediaRecorder do Safari, deixa essas durações em
 * zero: vale o {@code mehd}, se houver, ou a soma das amostras dos fragmentos ({@code trun}) menos o atraso do
 * codificador da lista de edição. O {@code moov} pode vir depois do {@code mdat}: o arquivo é lido até o fim.
 */
final class IsoMedia {

    /** Caixas atravessadas: os filhos são lidos em sequência. */
    private static final Set<String> CONTAINERS = Set.of("moov", "trak", "mdia", "minf", "edts", "mvex", "moof",
            "traf");
    /** Caixas cujo começo é lido; as demais são puladas inteiras. */
    private static final Set<String> READ = Set.of("mvhd", "tkhd", "mdhd", "hdlr", "elst", "mehd", "trex", "tfhd");
    /** O começo de cada caixa lida cabe nisso; o resto é pulado. */
    private static final int HEAD = 32;

    private IsoMedia() {
    }

    static @Nullable Double seconds(ByteInput in) throws IOException {
        byte[] box = new byte[HEAD];
        long movieScale = 0;
        long movieDuration = 0;
        long fragmentedDuration = 0;
        List<Track> tracks = new ArrayList<>();
        Map<Long, Long> defaults = new HashMap<>();
        Map<Long, Long> fragments = new HashMap<>();
        long fragmentTrack = -1;
        long fragmentDefault = -1;
        while (in.readFully(box, 0, 8)) {
            long size = ByteInput.u32be(box, 0);
            String type = new String(box, 4, 4, StandardCharsets.ISO_8859_1);
            long header = 8;
            if (size == 1) {
                if (!in.readFully(box, 8, 8)) {
                    break;
                }
                size = ByteInput.u64be(box, 8);
                header = 16;
            }
            if (CONTAINERS.contains(type)) {
                if (type.equals("trak")) {
                    tracks.add(new Track());
                }
                continue;
            }
            // Tamanho 0 é "até o fim do arquivo": nada de útil depois dele.
            if (size < header) {
                break;
            }
            long body = size - header;
            Track track = tracks.isEmpty() ? null : tracks.getLast();
            int length = (int) Math.min(body, HEAD);
            boolean known = READ.contains(type);
            if (known && !in.readFully(box, 0, length)) {
                break;
            }
            boolean v1 = box[0] == 1;
            switch (type) {
                case "mvhd" -> {
                    movieScale = field(box, length, v1 ? 20 : 12, 4);
                    movieDuration = field(box, length, v1 ? 24 : 16, v1 ? 8 : 4);
                }
                case "tkhd" -> {
                    if (track != null) {
                        track.id = field(box, length, v1 ? 20 : 12, 4);
                    }
                }
                case "mdhd" -> {
                    if (track != null) {
                        track.scale = field(box, length, v1 ? 20 : 12, 4);
                        track.duration = field(box, length, v1 ? 24 : 16, v1 ? 8 : 4);
                    }
                }
                case "hdlr" -> {
                    if (track != null && length >= 12) {
                        track.handler = new String(box, 8, 4, StandardCharsets.ISO_8859_1);
                    }
                }
                case "elst" -> {
                    // Primeira edição com mídia: o início pulado (amostras de preparo do AAC).
                    if (track != null && field(box, length, 4, 4) > 0) {
                        long start = v1 ? field(box, length, 16, 8) : (int) field(box, length, 12, 4);
                        track.skipped = Math.max(0, start);
                    }
                }
                case "mehd" -> fragmentedDuration = field(box, length, 4, v1 ? 8 : 4);
                case "trex" -> defaults.put(field(box, length, 4, 4), field(box, length, 12, 4));
                case "tfhd" -> {
                    long flags = field(box, length, 1, 3);
                    fragmentTrack = field(box, length, 4, 4);
                    int offset = 8 + ((flags & 0x01) != 0 ? 8 : 0) + ((flags & 0x02) != 0 ? 4 : 0);
                    fragmentDefault = (flags & 0x08) != 0 ? field(box, length, offset, 4)
                            : defaults.getOrDefault(fragmentTrack, -1L);
                }
                case "trun" -> {
                    long samples = trun(in, body, fragmentDefault);
                    if (samples < 0) {
                        return result(movieScale, movieDuration, fragmentedDuration, tracks, fragments);
                    }
                    fragments.merge(fragmentTrack, samples, Long::sum);
                    continue;
                }
                default -> {
                    // Caixa sem interesse (mdat, stbl, udta...).
                }
            }
            if (!in.skip(body - (known ? length : 0))) {
                break;
            }
        }
        return result(movieScale, movieDuration, fragmentedDuration, tracks, fragments);
    }

    private static @Nullable Double result(long movieScale, long movieDuration, long fragmentedDuration,
            List<Track> tracks, Map<Long, Long> fragments) {
        Track sound = tracks.stream().filter(track -> track.handler.equals("soun")).findFirst()
                .orElse(tracks.isEmpty() ? null : tracks.getFirst());
        if (movieScale > 0 && valid(movieDuration)) {
            return (double) movieDuration / movieScale;
        }
        if (sound != null && sound.scale > 0 && valid(sound.duration)) {
            return (double) sound.duration / sound.scale;
        }
        if (movieScale > 0 && valid(fragmentedDuration)) {
            return (double) fragmentedDuration / movieScale;
        }
        Long samples = sound == null ? null : fragments.get(sound.id);
        if (samples != null && sound.scale > 0) {
            return Math.max(0, samples - sound.skipped) / (double) sound.scale;
        }
        return null;
    }

    /** Zero e "todos os bits 1" querem dizer duração desconhecida. */
    private static boolean valid(long duration) {
        return duration > 0 && duration != 0xFFFFFFFFL;
    }

    /**
     * Soma a duração das amostras de um {@code trun}, lendo só os campos de duração; -1 se a caixa não fecha (mais
     * amostras do que bytes).
     */
    private static long trun(ByteInput in, long body, long fallback) throws IOException {
        byte[] head = new byte[8];
        if (body < 8 || !in.readFully(head, 0, 8)) {
            return -1;
        }
        long flags = ByteInput.u24be(head, 1);
        long count = ByteInput.u32be(head, 4);
        long optional = ((flags & 0x01) != 0 ? 4 : 0) + ((flags & 0x04) != 0 ? 4 : 0);
        int perSample = 4 * Long.bitCount(flags & 0xF00);
        long remaining = body - 8 - optional;
        if (remaining < 0 || count * perSample > remaining || !in.skip(optional)) {
            return -1;
        }
        long total = 0;
        if ((flags & 0x100) == 0) {
            total = fallback < 0 ? 0 : Math.multiplyExact(count, fallback);
        } else {
            byte[] sample = new byte[perSample];
            for (long i = 0; i < count; i++) {
                if (!in.readFully(sample, 0, perSample)) {
                    return -1;
                }
                total += ByteInput.u32be(sample, 0);
            }
            remaining -= count * perSample;
        }
        return in.skip(remaining) ? total : -1;
    }

    /** Inteiro sem sinal de {@code size} bytes em {@code offset}; 0 se a caixa lida não chega até lá. */
    private static long field(byte[] box, int length, int offset, int size) {
        if (offset + size > length) {
            return 0;
        }
        return switch (size) {
            case 3 -> ByteInput.u24be(box, offset);
            case 4 -> ByteInput.u32be(box, offset);
            default -> ByteInput.u64be(box, offset);
        };
    }

    /** Faixa declarada no {@code moov}. */
    private static final class Track {
        long id = -1;
        long scale;
        long duration;
        String handler = "";
        long skipped;
    }
}
