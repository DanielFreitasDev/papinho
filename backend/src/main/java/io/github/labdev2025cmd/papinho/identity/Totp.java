package io.github.labdev2025cmd.papinho.identity;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * TOTP da RFC 6238 (HMAC-SHA1, passo de 30 s, 6 dígitos), o formato que todos os apps autenticadores
 * aceitam. Usa apenas primitivas do JDK; aceita um passo de tolerância para relógios levemente desajustados.
 */
final class Totp {

    private static final int DIGITS = 6;
    private static final long STEP_SECONDS = 30;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Totp() {
    }

    /** Segredo novo de 160 bits em Base32 (sem preenchimento). */
    static String newSecret(SecureRandom random) {
        byte[] bytes = new byte[20];
        random.nextBytes(bytes);
        return base32(bytes);
    }

    /** URI {@code otpauth://} para o QR Code do app autenticador. */
    static String uri(String issuer, String account, String secret) {
        String label = encode(issuer) + ":" + encode(account);
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + encode(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    /** Confere o código no passo atual e nos vizinhos (±30 s), em tempo constante por passo. */
    static boolean verify(String secret, String code, Instant now) {
        return matchingStep(secret, code, now) >= 0;
    }

    /**
     * Passo de 30 s do código (atual ou vizinho), ou -1 se não confere; o login guarda o passo para o código não
     * valer duas vezes. Confere os três passos sempre, para não vazar qual bateu pelo tempo de resposta.
     */
    static long matchingStep(String secret, String code, Instant now) {
        if (code == null || !code.matches("^[0-9]{6}$")) {
            return -1;
        }
        long step = now.getEpochSecond() / STEP_SECONDS;
        long matched = -1;
        for (long offset = -1; offset <= 1; offset++) {
            if (MessageDigest.isEqual(generate(secret, step + offset).getBytes(), code.getBytes())) {
                matched = step + offset;
            }
        }
        return matched;
    }

    static String generate(String secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decodeBase32(secret), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24) | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8) | (hash[offset + 3] & 0xFF);
            return String.format(Locale.ROOT, "%06d", binary % 1_000_000);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA1 unavailable", exception);
        }
    }

    static String base32(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32.charAt((buffer >> (bits - 5)) & 0x1F));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32.charAt((buffer << (5 - bits)) & 0x1F));
        }
        return out.toString();
    }

    static byte[] decodeBase32(String value) {
        String clean = value.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        ByteBuffer out = ByteBuffer.allocate(clean.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char c : clean.toCharArray()) {
            int index = BASE32.indexOf(c);
            if (index < 0) {
                throw new IllegalArgumentException("Invalid Base32");
            }
            buffer = (buffer << 5) | index;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) ((buffer >> (bits - 8)) & 0xFF));
                bits -= 8;
            }
        }
        return java.util.Arrays.copyOf(out.array(), out.position());
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }
}
