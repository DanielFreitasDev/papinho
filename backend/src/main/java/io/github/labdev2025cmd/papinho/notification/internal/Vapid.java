package io.github.labdev2025cmd.papinho.notification.internal;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;

/**
 * Identificação do servidor no Web Push (VAPID, RFC 8292): JWT ES256 com {@code aud} (a origem do serviço de
 * push), {@code exp} e {@code sub} (contato da instância), enviado com a chave pública no cabeçalho
 * {@code Authorization: vapid t=..., k=...}.
 */
public final class Vapid {

    private static final Base64.Encoder URL = Base64.getUrlEncoder().withoutPadding();

    private final PrivateKey privateKey;
    private final String publicKey;

    public Vapid(PrivateKey privateKey, String publicKey) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }

    /** Par novo de chaves P-256: a pública em base64url (ponto não comprimido) e a privada em PKCS#8. */
    public static Generated generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair pair = generator.generateKeyPair();
            return new Generated(URL.encodeToString(WebPushCrypto.uncompressed((ECPublicKey) pair.getPublic())),
                    Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to generate VAPID keys", exception);
        }
    }

    public static Vapid load(String publicKey, String pkcs8) {
        try {
            PrivateKey key = KeyFactory.getInstance("EC")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pkcs8)));
            return new Vapid(key, publicKey);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Invalid VAPID private key", exception);
        }
    }

    /** Chave pública (base64url) que o navegador recebe como {@code applicationServerKey}. */
    public String publicKey() {
        return publicKey;
    }

    /** Valor do cabeçalho {@code Authorization} para um serviço de push. */
    public String authorization(String audience, String subject, Instant expiresAt) {
        String header = URL.encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String claims = URL.encodeToString(("{\"aud\":\"" + json(audience) + "\",\"exp\":"
                + expiresAt.getEpochSecond() + ",\"sub\":\"" + json(subject) + "\"}")
                .getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + claims;
        try {
            // P1363: assinatura crua r ‖ s de 64 bytes, como o JWS pede (não DER).
            Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
            signature.initSign(privateKey);
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return "vapid t=" + signingInput + "." + URL.encodeToString(signature.sign()) + ", k=" + publicKey;
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to sign VAPID token", exception);
        }
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** Chaves recém-geradas: a privada (PKCS#8 em Base64) precisa ser cifrada antes de ir para o banco. */
    public record Generated(String publicKey, String privateKeyPkcs8) {
    }
}
