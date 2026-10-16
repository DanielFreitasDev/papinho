package io.github.labdev2025cmd.papinho.notification.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Cifra do Web Push (RFC 8291) e assinatura VAPID (RFC 8292). */
class WebPushCryptoTest {

    private static final Base64.Decoder URL = Base64.getUrlDecoder();

    @Test
    @DisplayName("reproduz o exemplo da seção 5 da RFC 8291 byte a byte")
    void shouldMatchRfc8291Example() throws GeneralSecurityException {
        byte[] plaintext = "When I grow up, I want to be a watermelon".getBytes(StandardCharsets.UTF_8);
        byte[] asPublic = URL.decode("BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8");
        PrivateKey asPrivate = privateKey(URL.decode("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw"));
        byte[] uaPublic = URL.decode("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4");
        byte[] auth = URL.decode("BTBZMqHH6r4Tts7J_aSIgg");
        byte[] salt = URL.decode("DGv6ra1nlYgDCS1FRnbzlw");

        byte[] body = WebPushCrypto.encrypt(plaintext, uaPublic, auth,
                new KeyPair(WebPushCrypto.publicKey(asPublic), asPrivate), salt);

        assertThat(Base64.getUrlEncoder().withoutPadding().encodeToString(body)).isEqualTo(
                "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN");
    }

    @Test
    @DisplayName("o navegador decifra com a própria chave privada e o segredo de autenticação")
    void shouldDecryptOnTheReceiverSide() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair browser = generator.generateKeyPair();
        byte[] uaPublic = WebPushCrypto.uncompressed((ECPublicKey) browser.getPublic());
        byte[] auth = new byte[16];
        new java.security.SecureRandom().nextBytes(auth);
        byte[] message = "{\"title\":\"Ana\",\"body\":\"oi 👋\"}".getBytes(StandardCharsets.UTF_8);

        byte[] body = WebPushCrypto.encrypt(message, uaPublic, auth);

        // Lado do navegador, refeito a partir da RFC: cabeçalho, ECDH com a chave do servidor, HKDF e AES-GCM.
        ByteBuffer header = ByteBuffer.wrap(body);
        byte[] salt = new byte[16];
        header.get(salt);
        assertThat(header.getInt()).isEqualTo(4096);
        byte[] asPublic = new byte[header.get()];
        header.get(asPublic);
        byte[] ciphertext = Arrays.copyOfRange(body, header.position(), body.length);
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(browser.getPrivate());
        agreement.doPhase(WebPushCrypto.publicKey(asPublic), true);
        byte[] prkKey = WebPushCrypto.hmac(auth, agreement.generateSecret());
        byte[] ikm = WebPushCrypto.hmac(prkKey, WebPushCrypto.concat(
                "WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic, new byte[] {1}));
        byte[] prk = WebPushCrypto.hmac(salt, ikm);
        byte[] cek = Arrays.copyOf(WebPushCrypto.hmac(prk,
                "Content-Encoding: aes128gcm\0\1".getBytes(StandardCharsets.US_ASCII)), 16);
        byte[] nonce = Arrays.copyOf(WebPushCrypto.hmac(prk,
                "Content-Encoding: nonce\0\1".getBytes(StandardCharsets.US_ASCII)), 12);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] record = cipher.doFinal(ciphertext);

        assertThat(record[record.length - 1]).as("delimitador do último registro").isEqualTo((byte) 2);
        assertThat(Arrays.copyOf(record, record.length - 1)).isEqualTo(message);
    }

    @Test
    @DisplayName("recusa chave do navegador que não é ponto P-256 válido")
    void shouldRejectInvalidBrowserKey() {
        byte[] notOnCurve = new byte[65];
        notOnCurve[0] = 4;
        notOnCurve[64] = 1;
        assertThatThrownBy(() -> WebPushCrypto.publicKey(notOnCurve)).isInstanceOf(GeneralSecurityException.class);
        assertThatThrownBy(() -> WebPushCrypto.publicKey(new byte[33])).isInstanceOf(GeneralSecurityException.class);
    }

    @Test
    @DisplayName("VAPID: JWT ES256 verificável com a chave pública, com aud, exp e sub")
    void shouldSignVapidToken() throws GeneralSecurityException {
        Vapid.Generated generated = Vapid.generate();
        Vapid vapid = Vapid.load(generated.publicKey(), generated.privateKeyPkcs8());
        String header = vapid.authorization("https://fcm.googleapis.com", "mailto:admin@example.org",
                Instant.ofEpochSecond(1_800_000_000));

        assertThat(header).startsWith("vapid t=").endsWith(", k=" + generated.publicKey());
        String jwt = header.substring("vapid t=".length(), header.indexOf(", k="));
        String[] parts = jwt.split("\\.");
        assertThat(new String(URL.decode(parts[0]), StandardCharsets.UTF_8)).contains("\"alg\":\"ES256\"");
        assertThat(new String(URL.decode(parts[1]), StandardCharsets.UTF_8))
                .isEqualTo("{\"aud\":\"https://fcm.googleapis.com\",\"exp\":1800000000,"
                        + "\"sub\":\"mailto:admin@example.org\"}");
        Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(WebPushCrypto.publicKey(URL.decode(generated.publicKey())));
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(URL.decode(parts[2])).hasSize(64);
        assertThat(verifier.verify(URL.decode(parts[2]))).isTrue();
    }

    private static PrivateKey privateKey(byte[] scalar) throws GeneralSecurityException {
        return KeyFactory.getInstance("EC").generatePrivate(
                new ECPrivateKeySpec(new BigInteger(1, scalar), WebPushCrypto.p256()));
    }
}
