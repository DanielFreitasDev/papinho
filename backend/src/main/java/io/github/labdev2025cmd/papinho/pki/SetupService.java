package io.github.labdev2025cmd.papinho.pki;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateEncodingException;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import io.github.labdev2025cmd.papinho.pki.internal.PkiStore;

/**
 * Lê a CA gerada por {@code pki init} e produz os artefatos da página de instalação (PK-03, PK-04):
 * certificado em DER e PEM, perfil de configuração do iOS, script de instalação para Linux e QR Code.
 *
 * <p>O certificado é recarregado quando o arquivo muda em disco, para que um {@code pki init --force-ca}
 * não exija reiniciar o servidor.</p>
 */
@Service
@ConditionalOnBooleanProperty("app.pki.enabled")
class SetupService {

    private final PkiProperties properties;
    private final PkiStore store;
    private final AtomicReference<@Nullable Cached> cache = new AtomicReference<>();

    SetupService(PkiProperties properties) {
        this.properties = properties;
        this.store = new PkiStore(properties.dir());
    }

    SetupMaterial material() {
        try {
            FileTime modified = Files.getLastModifiedTime(store.resolve(PkiStore.CA_CERT_PEM));
            Cached current = cache.get();
            if (current != null && current.modified().equals(modified)) {
                return current.material();
            }
            var certificate = store.readCaCertificate();
            String hostname = store.readMetadata().hostname();
            SetupMaterial material = new SetupMaterial(hostname, certificate, PkiStore.sha256Fingerprint(certificate));
            cache.set(new Cached(modified, material));
            return material;
        } catch (IOException exception) {
            throw new UncheckedIOException("PKI not initialized at " + store.directory(), exception);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Invalid CA certificate at " + store.directory(), exception);
        }
    }

    byte[] caDer() {
        try {
            return material().caCertificate().getEncoded();
        } catch (CertificateEncodingException exception) {
            throw new IllegalStateException("Unable to encode CA certificate", exception);
        }
    }

    String caPem() {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(caDer());
        return "-----BEGIN CERTIFICATE-----\n" + body + "\n-----END CERTIFICATE-----\n";
    }

    String setupUrl() {
        return "http://" + properties.httpAuthority(material().hostname()) + "/setup";
    }

    String appUrl() {
        return "https://" + properties.httpsAuthority(material().hostname()) + "/";
    }

    /** Script de instalação com a URL e a impressão digital esperada embutidas. */
    String installScript() {
        SetupMaterial material = material();
        return template("pki/install-ca.sh")
                .replace("@@CA_URL@@", "http://" + properties.httpAuthority(material.hostname()) + "/setup/ca.pem")
                .replace("@@FINGERPRINT@@", material.compactFingerprint())
                .replace("@@HOSTNAME_PORT@@", properties.httpAuthority(material.hostname()))
                .replace("@@HOSTNAME_HTTPS@@", properties.httpsAuthority(material.hostname()))
                .replace("@@HOSTNAME@@", material.hostname());
    }

    /**
     * Perfil {@code .mobileconfig} do iOS/iPadOS com a CA. Depois de instalar o perfil, o usuário ainda precisa
     * ligar a confiança total em Ajustes › Geral › Sobre › Ajustes de Confiança de Certificado.
     */
    String mobileConfig() {
        SetupMaterial material = material();
        String hostname = HtmlUtils.htmlEscape(material.hostname());
        String certificate = Base64.getEncoder().encodeToString(caDer());
        // UUIDs determinísticos: reinstalar o mesmo perfil substitui o anterior em vez de duplicar.
        String payloadUuid = UUID.nameUUIDFromBytes(("papinho-ca-payload-" + material.sha256Fingerprint())
                .getBytes(StandardCharsets.UTF_8)).toString().toUpperCase(java.util.Locale.ROOT);
        String profileUuid = UUID.nameUUIDFromBytes(("papinho-ca-profile-" + hostname)
                .getBytes(StandardCharsets.UTF_8)).toString().toUpperCase(java.util.Locale.ROOT);
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0">
                <dict>
                  <key>PayloadContent</key>
                  <array>
                    <dict>
                      <key>PayloadCertificateFileName</key>
                      <string>papinho-ca.crt</string>
                      <key>PayloadContent</key>
                      <data>%s</data>
                      <key>PayloadDescription</key>
                      <string>CA da rede local do Papinho, restrita a %s</string>
                      <key>PayloadDisplayName</key>
                      <string>Papinho LAN CA</string>
                      <key>PayloadIdentifier</key>
                      <string>lan.papinho.ca.%s</string>
                      <key>PayloadType</key>
                      <string>com.apple.security.root</string>
                      <key>PayloadUUID</key>
                      <string>%s</string>
                      <key>PayloadVersion</key>
                      <integer>1</integer>
                    </dict>
                  </array>
                  <key>PayloadDisplayName</key>
                  <string>Papinho (%s)</string>
                  <key>PayloadIdentifier</key>
                  <string>lan.papinho.profile.%s</string>
                  <key>PayloadRemovalDisallowed</key>
                  <false/>
                  <key>PayloadType</key>
                  <string>Configuration</string>
                  <key>PayloadUUID</key>
                  <string>%s</string>
                  <key>PayloadVersion</key>
                  <integer>1</integer>
                </dict>
                </plist>
                """.formatted(certificate, hostname, hostname, payloadUuid, hostname, hostname, profileUuid);
    }

    /** QR Code em SVG apontando para a página de instalação (facilita abrir no celular). */
    String qrCodeSvg() {
        try {
            var hints = new java.util.EnumMap<EncodeHintType, Object>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 2);
            BitMatrix matrix = new QRCodeWriter().encode(setupUrl(), BarcodeFormat.QR_CODE, 0, 0, hints);
            StringBuilder path = new StringBuilder();
            for (int y = 0; y < matrix.getHeight(); y++) {
                for (int x = 0; x < matrix.getWidth(); x++) {
                    if (matrix.get(x, y)) {
                        path.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
                    }
                }
            }
            return """
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %d %d" shape-rendering="crispEdges" \
                    role="img" aria-label="QR Code da página de instalação"><rect width="100%%" height="100%%" \
                    fill="#fff"/><path fill="#000" d="%s"/></svg>
                    """.formatted(matrix.getWidth(), matrix.getHeight(), path);
        } catch (WriterException exception) {
            throw new IllegalStateException("Unable to render QR code", exception);
        }
    }

    static String template(String path) {
        try (InputStream input = new ClassPathResource(path).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Missing classpath template " + path, exception);
        }
    }

    private record Cached(FileTime modified, SetupMaterial material) {
    }
}
