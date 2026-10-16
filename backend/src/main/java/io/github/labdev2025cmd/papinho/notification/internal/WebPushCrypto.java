package io.github.labdev2025cmd.papinho.notification.internal;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cifra do conteúdo do Web Push (RFC 8291, "aes128gcm" da RFC 8188) só com o JDK: ECDH P-256 entre uma chave
 * efêmera e a chave do navegador, HKDF-SHA-256 com o segredo de autenticação, AES-128-GCM num registro único. O
 * serviço de push do navegador (Google, Mozilla, Apple) só vê o texto cifrado.
 */
public final class WebPushCrypto {

    /** Tamanho do registro anunciado no cabeçalho; o conteúdo cabe sempre num registro só. */
    static final int RECORD_SIZE = 4096;

    private static final byte[] KEY_INFO = "WebPush: info\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CEK_INFO = "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] NONCE_INFO = "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII);

    private WebPushCrypto() {
    }

    /** Cifra para o navegador ({@code p256dh} e {@code auth} da assinatura) com chave efêmera e sal novos. */
    public static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            return encrypt(plaintext, uaPublic, authSecret, generator.generateKeyPair(), salt);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to encrypt push message", exception);
        }
    }

    /** Versão determinística (chave do servidor e sal dados), para conferir com o exemplo da RFC 8291. */
    static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret, KeyPair asKeys, byte[] salt)
            throws GeneralSecurityException {
        if (plaintext.length > RECORD_SIZE - 17) {
            throw new IllegalArgumentException("push message too large");
        }
        byte[] asPublic = uncompressed((ECPublicKey) asKeys.getPublic());
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(asKeys.getPrivate());
        agreement.doPhase(publicKey(uaPublic), true);
        byte[] ecdhSecret = agreement.generateSecret();

        byte[] prkKey = hmac(authSecret, ecdhSecret);
        byte[] ikm = hmac(prkKey, concat(KEY_INFO, uaPublic, asPublic, new byte[] {1}));
        byte[] prk = hmac(salt, ikm);
        byte[] cek = Arrays.copyOf(hmac(prk, concat(CEK_INFO, new byte[] {1})), 16);
        byte[] nonce = Arrays.copyOf(hmac(prk, concat(NONCE_INFO, new byte[] {1})), 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        // Último (e único) registro: delimitador 0x02 sem preenchimento.
        byte[] ciphertext = cipher.doFinal(concat(plaintext, new byte[] {2}));
        return ByteBuffer.allocate(16 + 4 + 1 + asPublic.length + ciphertext.length).put(salt).putInt(RECORD_SIZE)
                .put((byte) asPublic.length).put(asPublic).put(ciphertext).array();
    }

    /** Ponto não comprimido (0x04 ‖ x ‖ y, 65 bytes), o formato do {@code p256dh} e da chave VAPID. */
    public static byte[] uncompressed(ECPublicKey key) {
        byte[] x = unsigned(key.getW().getAffineX());
        byte[] y = unsigned(key.getW().getAffineY());
        return concat(new byte[] {4}, x, y);
    }

    /** Chave pública P-256 a partir do ponto não comprimido; recusa formato ou ponto inválido. */
    public static ECPublicKey publicKey(byte[] uncompressed) throws GeneralSecurityException {
        if (uncompressed.length != 65 || uncompressed[0] != 4) {
            throw new GeneralSecurityException("not an uncompressed P-256 point");
        }
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 33));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(uncompressed, 33, 65));
        ECParameterSpec curve = p256();
        // O KeyFactory do JDK não confere se o ponto está na curva: y² = x³ + ax + b (mod p). Ponto fora dela
        // abriria um ataque de curva inválida no ECDH.
        BigInteger p = ((java.security.spec.ECFieldFp) curve.getCurve().getField()).getP();
        BigInteger left = y.modPow(BigInteger.TWO, p);
        BigInteger right = x.pow(3).add(curve.getCurve().getA().multiply(x)).add(curve.getCurve().getB()).mod(p);
        if (x.compareTo(p) >= 0 || y.compareTo(p) >= 0 || !left.equals(right)) {
            throw new GeneralSecurityException("point is not on P-256");
        }
        return (ECPublicKey) KeyFactory.getInstance("EC")
                .generatePublic(new ECPublicKeySpec(new ECPoint(x, y), curve));
    }

    static ECParameterSpec p256() throws GeneralSecurityException {
        java.security.AlgorithmParameters parameters = java.security.AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        return parameters.getParameterSpec(ECParameterSpec.class);
    }

    static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        byte[] result = new byte[32];
        int length = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - length, result, 32 - length, length);
        return result;
    }

    static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length += part.length;
        }
        ByteBuffer buffer = ByteBuffer.allocate(length);
        for (byte[] part : parts) {
            buffer.put(part);
        }
        return buffer.array();
    }
}
