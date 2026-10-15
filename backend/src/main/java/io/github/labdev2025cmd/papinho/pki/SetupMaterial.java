package io.github.labdev2025cmd.papinho.pki;

import java.security.cert.X509Certificate;

/**
 * Material público exibido na página {@code /setup}: a CA e suas impressões digitais.
 *
 * @param hostname hostname da instância
 * @param caCertificate certificado da CA
 * @param sha256Fingerprint impressão digital SHA-256 formatada com dois-pontos
 */
record SetupMaterial(String hostname, X509Certificate caCertificate, String sha256Fingerprint) {

    /** Impressão digital em hexadecimal minúsculo contínuo, como {@code sha256sum} mostra. */
    String compactFingerprint() {
        return sha256Fingerprint.replace(":", "").toLowerCase(java.util.Locale.ROOT);
    }
}
