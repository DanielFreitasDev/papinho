package io.github.labdev2025cmd.papinho.media.internal.audio;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

/**
 * Duração de um áudio lida do próprio arquivo, sem decodificar, para o cliente mostrar o tempo antes de tocar
 * (AU-02). Lê o arquivo uma vez, em sequência, e cobre os contêineres aceitos como áudio: Ogg (Opus, Vorbis,
 * FLAC, Speex), WebM/Matroska (inclusive o do MediaRecorder, sem duração no cabeçalho), MP4/M4A (inclusive o
 * fragmentado do Safari), MP3, AAC (ADTS), WAV e FLAC. Arquivo estranho, truncado ou forjado dá {@code null},
 * nunca exceção.
 */
public final class AudioDuration {

    /** Acima disso o valor só pode vir de arquivo forjado: 1 GB de áudio a 8 kbit/s dá menos de 12 dias. */
    static final long MAX_MILLIS = Duration.ofDays(30).toMillis();

    private AudioDuration() {
    }

    /**
     * Duração em milissegundos, ou {@code null} se o tipo não for de áudio conhecido ou o arquivo não disser.
     *
     * @param content o arquivo, lido até onde for preciso (quem chama fecha)
     * @param contentType tipo detectado pelo conteúdo
     */
    public static @Nullable Long millis(InputStream content, String contentType) {
        ByteInput in = new ByteInput(content);
        try {
            Double seconds = switch (contentType.toLowerCase(Locale.ROOT).split(";", 2)[0].strip()) {
                case "audio/ogg", "audio/opus", "audio/vorbis", "application/ogg" -> Ogg.seconds(in);
                case "audio/webm", "audio/x-matroska" -> Matroska.seconds(in);
                case "audio/mp4", "audio/x-m4a" -> IsoMedia.seconds(in);
                case "audio/mpeg" -> MpegAudio.seconds(in);
                case "audio/aac", "audio/x-aac" -> Adts.seconds(in);
                case "audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave" -> Wave.seconds(in);
                case "audio/flac", "audio/x-flac" -> Flac.seconds(in);
                default -> null;
            };
            if (seconds == null || !(seconds >= 0)) {
                return null;
            }
            long millis = Math.round(seconds * 1000);
            return millis <= MAX_MILLIS ? millis : null;
        } catch (IOException | RuntimeException unreadable) {
            // Conta estourada, índice fora do bloco lido ou falha de leitura: arquivo que não diz a duração.
            return null;
        }
    }
}
