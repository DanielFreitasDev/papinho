package io.github.labdev2025cmd.papinho.media;

import java.text.Normalizer;

/**
 * Nomes de arquivo enviados pelo usuário (MD-01): só para exibir e para o download, nunca para o caminho no
 * disco. Remove diretórios, caracteres de controle e reservados, limita o tamanho e preserva a extensão.
 */
public final class FileNames {

    static final int MAX_LENGTH = 200;
    private static final String FALLBACK = "arquivo";

    private FileNames() {
    }

    public static String sanitize(String raw) {
        String name = Normalizer.normalize(raw, Normalizer.Form.NFC);
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        name = name.substring(slash + 1);
        StringBuilder clean = new StringBuilder(name.length());
        name.codePoints().forEach(cp -> {
            boolean reserved = cp < 0x20 || cp == 0x7F || "<>:\"|?*".indexOf(cp) >= 0
                    || Character.getType(cp) == Character.FORMAT || (cp >= 0x80 && cp < 0xA0);
            clean.appendCodePoint(reserved ? '_' : cp);
        });
        String result = clean.toString().strip();
        while (result.startsWith(".")) {
            result = result.substring(1);
        }
        while (result.endsWith(".") || result.endsWith(" ")) {
            result = result.substring(0, result.length() - 1);
        }
        if (result.isBlank()) {
            return FALLBACK;
        }
        if (result.codePointCount(0, result.length()) > MAX_LENGTH) {
            int dot = result.lastIndexOf('.');
            String extension = dot > 0 && result.length() - dot <= 10 ? result.substring(dot) : "";
            String base = result.substring(0, result.offsetByCodePoints(0, MAX_LENGTH - extension.length()));
            result = base + extension;
        }
        return result;
    }
}
