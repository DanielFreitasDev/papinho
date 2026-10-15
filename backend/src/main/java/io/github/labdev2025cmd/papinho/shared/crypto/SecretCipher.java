package io.github.labdev2025cmd.papinho.shared.crypto;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cifra segredos guardados no banco (segredo TOTP, chaves de provedores de transcrição) com AES-256-GCM.
 *
 * <p>A chave vem de {@code app.crypto.key} (Base64, 32 bytes, recomendado em produção) ou, se ausente, de um
 * arquivo gerado na primeira execução em {@code app.crypto.key-file} com permissão 0600. Perder a chave
 * torna os segredos ilegíveis; ela entra no backup da instância, nunca no repositório.</p>
 *
 * <p>Formato do texto cifrado: {@code v1:} + Base64(IV de 12 bytes ‖ texto cifrado ‖ tag de 16 bytes).</p>
 */
@Component
public class SecretCipher {

    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(CryptoProperties properties) {
        this.key = loadKey(properties);
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to encrypt secret", exception);
        }
    }

    public String decrypt(String ciphertext) {
        if (!ciphertext.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Unknown secret format");
        }
        try {
            byte[] data = Base64.getDecoder().decode(ciphertext.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES));
            return new String(cipher.doFinal(data, IV_BYTES, data.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to decrypt secret", exception);
        }
    }

    private static SecretKey loadKey(CryptoProperties properties) {
        if (properties.key() != null && !properties.key().isBlank()) {
            byte[] bytes = Base64.getDecoder().decode(properties.key().trim());
            if (bytes.length != 32) {
                throw new IllegalStateException("app.crypto.key must be 32 bytes encoded in Base64");
            }
            return new SecretKeySpec(bytes, "AES");
        }
        Path file = properties.keyFile();
        try {
            if (Files.isRegularFile(file)) {
                return new SecretKeySpec(Base64.getDecoder().decode(Files.readString(file).trim()), "AES");
            }
            Files.createDirectories(file.toAbsolutePath().getParent());
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256);
            SecretKey generated = generator.generateKey();
            if (Files.getFileAttributeView(file.toAbsolutePath().getParent(), PosixFileAttributeView.class) != null) {
                Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            } else {
                Files.createFile(file);
            }
            Files.writeString(file, Base64.getEncoder().encodeToString(generated.getEncoded()));
            return generated;
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to load or create the secret key file " + file, exception);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("AES unavailable", exception);
        }
    }

    /**
     * Origem da chave de cifra (prefixo {@code app.crypto}).
     *
     * @param key chave AES-256 em Base64; tem precedência sobre o arquivo
     * @param keyFile arquivo da chave gerada automaticamente
     */
    @ConfigurationProperties(prefix = "app.crypto")
    public record CryptoProperties(@org.jspecify.annotations.Nullable String key, Path keyFile) {
    }
}
