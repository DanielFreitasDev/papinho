package io.github.labdev2025cmd.papinho.pki.internal;

import java.math.BigInteger;
import java.net.InetAddress;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.util.Date;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.GeneralSubtree;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.NameConstraints;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * Emite a CA da LAN e o certificado do servidor com BouncyCastle (ADR 0007, PK-01).
 *
 * <ul>
 *   <li>CA: ECDSA P-256, 10 anos, {@code pathLen=0} e <b>Name Constraints</b> críticas que só permitem o
 *   hostname configurado (e seus subdomínios) e a faixa de IP da LAN. Uma CA instalada nos aparelhos dos
 *   amigos não consegue validar certificados para outros sites.</li>
 *   <li>Servidor: ECDSA P-256, validade de 397 dias (abaixo do teto de 398 dos navegadores), SAN com o
 *   hostname e o IP da LAN, EKU {@code serverAuth}.</li>
 * </ul>
 *
 * <p>Classe sem estado; cada chamada gera material novo com {@link SecureRandom}.</p>
 */
public final class CertificateAuthority {

    /** Validade da CA. */
    public static final Duration CA_VALIDITY = Duration.ofDays(3650);
    /** Validade do certificado do servidor: 397 dias, margem de um dia sob o limite de 398. */
    public static final Duration SERVER_VALIDITY = Duration.ofDays(397);

    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";
    private static final Duration BACKDATE = Duration.ofHours(1);

    private final SecureRandom random = new SecureRandom();

    /** Par de chaves ECDSA na curva P-256 (secp256r1). */
    public KeyPair generateKeyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"), random);
        return generator.generateKeyPair();
    }

    /**
     * Cria o certificado autoassinado da CA restrito ao hostname e à rede informados.
     *
     * @param keyPair chaves da CA
     * @param hostname hostname permitido (por exemplo {@code papinho.lan}); subdomínios também são permitidos
     * @param network faixa de IPv4 permitida, em notação CIDR (por exemplo {@code 192.168.0.0/24})
     * @param now instante de referência para a validade
     */
    public X509Certificate createCaCertificate(KeyPair keyPair, String hostname, String network, Instant now)
            throws GeneralSecurityException {
        X500Name subject = new X500NameBuilder(BCStyle.INSTANCE)
                .addRDN(BCStyle.O, "Papinho")
                .addRDN(BCStyle.CN, "Papinho LAN CA " + hostname)
                .build();
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(subject, serial(),
                Date.from(now.minus(BACKDATE)), Date.from(now.plus(CA_VALIDITY)), subject, keyPair.getPublic());
        JcaX509ExtensionUtils extensions = new JcaX509ExtensionUtils();
        GeneralSubtree[] permitted = {
                new GeneralSubtree(new GeneralName(GeneralName.dNSName, hostname)),
                new GeneralSubtree(new GeneralName(GeneralName.iPAddress, normalizeNetwork(network))),
        };
        try {
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(0));
            builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
            builder.addExtension(Extension.subjectKeyIdentifier, false,
                    extensions.createSubjectKeyIdentifier(keyPair.getPublic()));
            builder.addExtension(Extension.nameConstraints, true, new NameConstraints(permitted, null));
        } catch (java.io.IOException exception) {
            throw new GeneralSecurityException("Unable to encode CA extensions", exception);
        }
        return sign(builder, keyPair.getPrivate());
    }

    /**
     * Emite o certificado TLS do servidor assinado pela CA.
     *
     * @param serverKeyPair chaves do servidor
     * @param caCertificate certificado da CA emissora
     * @param caKey chave privada da CA
     * @param hostname nome DNS do servidor (SAN e CN)
     * @param ipAddress IP do servidor na LAN (SAN); precisa estar dentro da faixa permitida pela CA
     * @param now instante de referência para a validade
     */
    public X509Certificate issueServerCertificate(KeyPair serverKeyPair, X509Certificate caCertificate,
            PrivateKey caKey, String hostname, String ipAddress, Instant now) throws GeneralSecurityException {
        Instant notAfter = now.plus(SERVER_VALIDITY);
        if (notAfter.isAfter(caCertificate.getNotAfter().toInstant())) {
            notAfter = caCertificate.getNotAfter().toInstant();
        }
        X500Name subject = new X500NameBuilder(BCStyle.INSTANCE).addRDN(BCStyle.CN, hostname).build();
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(caCertificate, serial(),
                Date.from(now.minus(BACKDATE)), Date.from(notAfter), subject, serverKeyPair.getPublic());
        JcaX509ExtensionUtils extensions = new JcaX509ExtensionUtils();
        GeneralNames alternativeNames = new GeneralNames(new GeneralName[] {
                new GeneralName(GeneralName.dNSName, hostname),
                new GeneralName(GeneralName.iPAddress, ipAddress),
        });
        try {
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
            builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
            builder.addExtension(Extension.extendedKeyUsage, false,
                    new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));
            builder.addExtension(Extension.subjectAlternativeName, false, alternativeNames);
            builder.addExtension(Extension.subjectKeyIdentifier, false,
                    extensions.createSubjectKeyIdentifier(serverKeyPair.getPublic()));
            builder.addExtension(Extension.authorityKeyIdentifier, false,
                    extensions.createAuthorityKeyIdentifier(caCertificate));
        } catch (java.io.IOException exception) {
            throw new GeneralSecurityException("Unable to encode server certificate extensions", exception);
        }
        return sign(builder, caKey);
    }

    /**
     * Normaliza a faixa CIDR para o endereço de rede ({@code 192.168.0.10/24} vira {@code 192.168.0.0/24}).
     *
     * @throws IllegalArgumentException se não for um IPv4 CIDR válido
     */
    public static String normalizeNetwork(String network) {
        String[] parts = network.trim().split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Network must be in CIDR notation: " + network);
        }
        int prefix = Integer.parseInt(parts[1]);
        if (prefix < 8 || prefix > 32) {
            throw new IllegalArgumentException("CIDR prefix must be between 8 and 32: " + network);
        }
        byte[] address = parseIpv4(parts[0]);
        int value = ((address[0] & 0xFF) << 24) | ((address[1] & 0xFF) << 16) | ((address[2] & 0xFF) << 8)
                | (address[3] & 0xFF);
        int mask = prefix == 0 ? 0 : -1 << (32 - prefix);
        int base = value & mask;
        return ((base >>> 24) & 0xFF) + "." + ((base >>> 16) & 0xFF) + "." + ((base >>> 8) & 0xFF) + "."
                + (base & 0xFF) + "/" + prefix;
    }

    /** Diz se o IPv4 está dentro da faixa CIDR. */
    public static boolean networkContains(String network, String ipAddress) {
        String normalized = normalizeNetwork(network);
        int prefix = Integer.parseInt(normalized.substring(normalized.indexOf('/') + 1));
        String candidate = normalizeNetwork(ipAddress + "/" + prefix);
        return candidate.equals(normalized);
    }

    /** Valida e converte um IPv4 literal (sem resolver DNS). */
    public static byte[] parseIpv4(String ipAddress) {
        String[] octets = ipAddress.trim().split("\\.");
        if (octets.length != 4) {
            throw new IllegalArgumentException("Not an IPv4 address: " + ipAddress);
        }
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            int octet = Integer.parseInt(octets[i]);
            if (octet < 0 || octet > 255) {
                throw new IllegalArgumentException("Not an IPv4 address: " + ipAddress);
            }
            bytes[i] = (byte) octet;
        }
        return bytes;
    }

    /** Prova de que o literal é um IPv4 utilizável no SAN. */
    public static InetAddress toInetAddress(String ipAddress) {
        try {
            return InetAddress.getByAddress(parseIpv4(ipAddress));
        } catch (java.net.UnknownHostException exception) {
            throw new IllegalArgumentException("Not an IPv4 address: " + ipAddress, exception);
        }
    }

    private BigInteger serial() {
        // Serial positivo de 159 bits (RFC 5280 limita a 20 octetos).
        return new BigInteger(159, random);
    }

    private static X509Certificate sign(X509v3CertificateBuilder builder, PrivateKey key)
            throws GeneralSecurityException {
        try {
            ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).build(key);
            return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
        } catch (OperatorCreationException exception) {
            throw new GeneralSecurityException("Unable to sign certificate", exception);
        }
    }

    /** Data de expiração formatada em UTC, para mensagens do CLI. */
    public static String formatExpiry(X509Certificate certificate) {
        return ZonedDateTime.ofInstant(certificate.getNotAfter().toInstant(), ZoneOffset.UTC).toLocalDate().toString();
    }
}
