package io.github.labdev2025cmd.papinho.media;

import java.util.Locale;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/** Decide o tipo de mídia e o que pode ser exibido no navegador a partir do tipo detectado pelo Tika. */
final class ContentTypes {

    /** Imagens que o navegador mostra e que o servidor trata (sem SVG: pode ter script). */
    static final Set<String> IMAGES = Set.of("image/jpeg", "image/png", "image/gif", "image/webp");
    private static final Set<String> VIDEOS = Set.of("video/mp4", "video/webm", "video/quicktime");
    private static final Set<String> AUDIOS = Set.of("audio/ogg", "audio/webm", "audio/mp4", "audio/mpeg",
            "audio/aac", "audio/wav", "audio/x-wav", "audio/vnd.wave", "audio/opus", "audio/x-m4a");

    private ContentTypes() {
    }

    /**
     * Tipo final do arquivo. Gravações de voz em WebM/Ogg/MP4 são detectadas como contêiner de vídeo ou
     * genérico; quando o cliente declarou áudio e o contêiner serve para áudio, vale o áudio.
     */
    static String resolve(String detected, @Nullable String declared) {
        String type = detected.toLowerCase(Locale.ROOT);
        String hint = declared == null ? "" : declared.toLowerCase(Locale.ROOT).split(";", 2)[0].strip();
        if (hint.startsWith("audio/")) {
            switch (type) {
                case "video/webm", "audio/webm" -> {
                    return "audio/webm";
                }
                case "application/ogg", "audio/ogg", "audio/vorbis", "audio/opus" -> {
                    return "audio/ogg";
                }
                case "video/mp4", "audio/mp4", "video/quicktime", "audio/x-m4a" -> {
                    return "audio/mp4";
                }
                default -> {
                    return type;
                }
            }
        }
        return type;
    }

    static MediaKind kind(String type) {
        if (IMAGES.contains(type)) {
            return MediaKind.IMAGE;
        }
        if (VIDEOS.contains(type)) {
            return MediaKind.VIDEO;
        }
        if (AUDIOS.contains(type)) {
            return MediaKind.AUDIO;
        }
        return MediaKind.DOCUMENT;
    }

    /** Só imagem, vídeo e áudio conhecidos abrem no navegador; o resto é sempre download. */
    static boolean inline(String type) {
        return IMAGES.contains(type) || VIDEOS.contains(type) || AUDIOS.contains(type);
    }
}
