package io.github.labdev2025cmd.papinho.identity;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * {@code @usuario} normalizado (ID-01): NFKC, minúsculas, só {@code [a-z0-9._]}, de 3 a 32 caracteres, sem
 * ponto no início, no fim ou repetido. A unicidade é garantida por constraint no banco sobre o valor
 * normalizado, o que torna o nome case-insensitive.
 *
 * @param value valor normalizado, sem o {@code @}
 */
public record Username(String value) {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 32;
    private static final Pattern ALLOWED = Pattern.compile("^[a-z0-9._]{3,32}$");
    private static final Pattern PREFIX = Pattern.compile("^[a-z0-9._]{1,32}$");

    public Username {
        if (!isValidNormalized(value)) {
            throw new InvalidUsernameException(value);
        }
    }

    /**
     * Normaliza a entrada do usuário (remove {@code @} inicial e espaços, aplica NFKC e minúsculas) e valida.
     *
     * @throws InvalidUsernameException se o resultado não for um {@code @usuario} válido
     */
    public static Username parse(String raw) {
        return new Username(normalize(raw));
    }

    /** Normalização sem validação; usada para buscas e para comparar com nomes reservados. */
    public static String normalize(String raw) {
        String trimmed = raw.strip();
        if (trimmed.startsWith("@")) {
            trimmed = trimmed.substring(1);
        }
        return Normalizer.normalize(trimmed, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    /** Pode ser o começo de algum {@code @usuario} normalizado (só caracteres permitidos, até o tamanho máximo)? */
    static boolean isPossiblePrefix(String value) {
        return PREFIX.matcher(value).matches();
    }

    static boolean isValidNormalized(String value) {
        return ALLOWED.matcher(value).matches() && !value.startsWith(".") && !value.endsWith(".")
                && !value.contains("..");
    }

    @Override
    public String toString() {
        return value;
    }
}
