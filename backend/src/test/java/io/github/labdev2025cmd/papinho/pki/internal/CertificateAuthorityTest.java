package io.github.labdev2025cmd.papinho.pki.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.NameConstraints;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CertificateAuthorityTest {

    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

    private final CertificateAuthority authority = new CertificateAuthority();
    private KeyPair caKeys;
    private X509Certificate ca;

    @BeforeEach
    void setUp() throws Exception {
        caKeys = authority.generateKeyPair();
        ca = authority.createCaCertificate(caKeys, "papinho.lan", "192.168.0.0/24", NOW);
    }

    @Test
    @DisplayName("CA usa ECDSA P-256, vale 10 anos e só pode emitir certificados finais")
    void shouldCreateP256CaValidForTenYears() {
        assertThat(ca.getPublicKey()).isInstanceOf(ECPublicKey.class);
        assertThat(((ECPublicKey) ca.getPublicKey()).getParams().getCurve().getField().getFieldSize()).isEqualTo(256);
        assertThat(ca.getSigAlgName()).isEqualTo("SHA256withECDSA");
        assertThat(Duration.between(NOW, ca.getNotAfter().toInstant())).isEqualTo(Duration.ofDays(3650));
        assertThat(ca.getBasicConstraints()).isZero();
        assertThat(ca.getKeyUsage()[5]).as("keyCertSign").isTrue();
    }

    @Test
    @DisplayName("CA tem Name Constraints críticas com o hostname e a faixa da LAN")
    void shouldConstrainCaToHostnameAndNetwork() throws Exception {
        assertThat(ca.getCriticalExtensionOIDs()).contains(Extension.nameConstraints.getId());
        NameConstraints constraints = NameConstraints.getInstance(
                org.bouncycastle.asn1.ASN1OctetString.getInstance(ca.getExtensionValue(
                        Extension.nameConstraints.getId())).getOctets());
        List<GeneralName> permitted = java.util.Arrays.stream(constraints.getPermittedSubtrees())
                .map(subtree -> subtree.getBase()).toList();
        assertThat(permitted).extracting(GeneralName::getTagNo)
                .containsExactly(GeneralName.dNSName, GeneralName.iPAddress);
        assertThat(permitted.getFirst().getName().toString()).isEqualTo("papinho.lan");
        byte[] ipAndMask = org.bouncycastle.asn1.ASN1OctetString.getInstance(permitted.get(1).getName()).getOctets();
        assertThat(ipAndMask).containsExactly(192, 168, 0, 0, 255, 255, 255, 0);
        assertThat(constraints.getExcludedSubtrees()).isNull();
    }

    @Test
    @DisplayName("certificado do servidor vale no máximo 398 dias e tem SAN com DNS e IP")
    void shouldIssueServerCertificateWithSanAndShortValidity() throws Exception {
        X509Certificate server = issue("papinho.lan", "192.168.0.10");

        long days = Duration.between(server.getNotBefore().toInstant(), server.getNotAfter().toInstant()).toDays();
        assertThat(days).isLessThanOrEqualTo(398);
        assertThat(server.getSubjectAlternativeNames()).extracting(entry -> (Object) entry.get(1))
                .containsExactly("papinho.lan", "192.168.0.10");
        assertThat(server.getExtendedKeyUsage()).containsExactly("1.3.6.1.5.5.7.3.1");
        assertThat(server.getBasicConstraints()).isEqualTo(-1);
        validate(server);
    }

    /*
     * Os PKIX do JDK e do BouncyCastle não aplicam as Name Constraints gravadas no certificado da âncora;
     * NSS (Firefox, Chrome no Linux), Chrome, Apple e OpenSSL aplicam. Por isso a prova usa o openssl verify.
     */

    @Test
    @DisplayName("CA instalada não valida certificado de outro domínio (Name Constraints, openssl)")
    void shouldRejectCertificateForOtherDomain(@TempDir Path dir) throws Exception {
        assumeOpenSsl();

        assertThat(opensslVerify(dir, issue("banco.example.com", "192.168.0.10")))
                .contains("permitted subtree violation");
    }

    @Test
    @DisplayName("CA instalada não valida certificado com IP fora da LAN (Name Constraints, openssl)")
    void shouldRejectCertificateForIpOutsideNetwork(@TempDir Path dir) throws Exception {
        assumeOpenSsl();

        assertThat(opensslVerify(dir, issue("papinho.lan", "8.8.8.8"))).contains("permitted subtree violation");
    }

    @Test
    @DisplayName("certificado do servidor e subdomínio do hostname passam no openssl verify")
    void shouldAcceptServerAndSubdomain(@TempDir Path dir) throws Exception {
        assumeOpenSsl();

        assertThat(opensslVerify(dir, issue("papinho.lan", "192.168.0.10"))).endsWith(": OK");
        assertThat(opensslVerify(dir, issue("chat.papinho.lan", "192.168.0.20"))).endsWith(": OK");
    }

    @Test
    @DisplayName("faixa CIDR é normalizada para o endereço de rede")
    void shouldNormalizeNetwork() {
        assertThat(CertificateAuthority.normalizeNetwork("192.168.0.10/24")).isEqualTo("192.168.0.0/24");
        assertThat(CertificateAuthority.normalizeNetwork("10.1.2.3/16")).isEqualTo("10.1.0.0/16");
        assertThat(CertificateAuthority.networkContains("192.168.0.0/24", "192.168.0.99")).isTrue();
        assertThat(CertificateAuthority.networkContains("192.168.0.0/24", "192.168.1.1")).isFalse();
        assertThatThrownBy(() -> CertificateAuthority.normalizeNetwork("192.168.0.1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CertificateAuthority.parseIpv4("300.1.1.1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void assumeOpenSsl() {
        boolean available;
        try {
            available = new ProcessBuilder("openssl", "version").start().waitFor() == 0;
        } catch (Exception exception) {
            available = false;
        }
        Assumptions.assumeTrue(available, "openssl não está instalado");
    }

    private String opensslVerify(Path dir, X509Certificate leaf) throws Exception {
        Path caFile = dir.resolve("ca.pem");
        Path leafFile = dir.resolve("leaf.pem");
        Files.write(caFile, PkiStore.toPem("CERTIFICATE", ca.getEncoded()));
        Files.write(leafFile, PkiStore.toPem("CERTIFICATE", leaf.getEncoded()));
        Process process = new ProcessBuilder("openssl", "verify", "-CAfile", caFile.toString(), leafFile.toString())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        process.waitFor();
        return output;
    }

    private X509Certificate issue(String hostname, String ip) throws Exception {
        return authority.issueServerCertificate(authority.generateKeyPair(), ca, caKeys.getPrivate(), hostname, ip,
                NOW);
    }

    /**
     * Valida com o PKIX do BouncyCastle, que aplica as Name Constraints do próprio certificado da âncora,
     * como fazem NSS, Chrome e Apple. O PKIX do JDK ignora essas restrições na âncora.
     */
    private void validate(X509Certificate certificate) throws Exception {
        var path = CertificateFactory.getInstance("X.509").generateCertPath(List.of(certificate));
        var parameters = new PKIXParameters(Set.of(new TrustAnchor(ca, null)));
        parameters.setRevocationEnabled(false);
        parameters.setDate(Date.from(NOW.plus(Duration.ofDays(1))));
        CertPathValidator.getInstance("PKIX", new org.bouncycastle.jce.provider.BouncyCastleProvider())
                .validate(path, parameters);
    }
}
