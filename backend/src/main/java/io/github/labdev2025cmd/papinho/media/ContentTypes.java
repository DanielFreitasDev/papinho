package io.github.labdev2025cmd.papinho.media;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/** Decide o tipo de mídia e o que pode ser exibido no navegador a partir do tipo detectado pelo Tika. */
final class ContentTypes {

    /** Imagens que o navegador mostra e que o servidor trata (sem SVG: pode ter script). */
    static final Set<String> IMAGES = Set.of("image/jpeg", "image/png", "image/gif", "image/webp");
    /** Fotos acima do limite de recodificação cujos metadados saem em fluxo ({@link PhotoMetadata}). */
    static final Set<String> STREAM_CLEANABLE_PHOTOS = Set.of("image/jpeg", "image/png");
    /** Fotos com EXIF que o servidor não decodifica nem limpa (HEIC/HEIF do iPhone, AVIF): recusadas (MD-02). */
    static final Set<String> UNCLEANABLE_PHOTOS = Set.of("image/heic", "image/heif", "image/heic-sequence",
            "image/heif-sequence", "image/avif");
    /** Documento que ganha número de páginas e prévia da primeira página ({@link PdfPreview}). */
    static final String PDF = "application/pdf";
    private static final Set<String> VIDEOS = Set.of("video/mp4", "video/webm", "video/quicktime");
    /** Os nomes antigos continuam aqui para os arquivos guardados antes da troca pelos nomes de {@link #ALIASES}. */
    private static final Set<String> AUDIOS = Set.of("audio/ogg", "audio/webm", "audio/mp4", "audio/mpeg",
            "audio/aac", "audio/wav", "audio/x-wav", "audio/vnd.wave", "audio/opus", "audio/x-m4a", "audio/flac");
    /**
     * Outros nomes que o Tika dá ao mesmo formato de áudio, trocados pelo que todo navegador toca: sem isso, AAC
     * (.aac) e FLAC viravam documento e o WAV ia como {@code audio/vnd.wave}.
     */
    private static final Map<String, String> ALIASES = Map.of("audio/x-aac", "audio/aac", "audio/x-flac",
            "audio/flac", "audio/vorbis", "audio/ogg", "audio/opus", "audio/ogg", "audio/x-wav", "audio/wav",
            "audio/vnd.wave", "audio/wav", "audio/wave", "audio/wav", "audio/x-m4a", "audio/mp4");

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
            type = switch (type) {
                case "video/webm" -> "audio/webm";
                case "application/ogg" -> "audio/ogg";
                case "video/mp4", "video/quicktime" -> "audio/mp4";
                default -> type;
            };
        }
        return ALIASES.getOrDefault(type, type);
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
