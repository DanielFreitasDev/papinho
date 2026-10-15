package io.github.labdev2025cmd.papinho.pki.internal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Layout em disco da PKI da LAN. Fica fora do repositório (padrão {@code ~/.papinho/pki}) e nunca é versionado.
 *
 * <pre>
 * pki/                  (0700)
 * ├── ca.key.pem        chave privada da CA, PKCS#8 (0600)
 * ├── ca.crt.pem        certificado da CA em PEM
 * ├── ca.crt            certificado da CA em DER (download para Windows, Android e iOS)
 * ├── server.key.pem    chave privada do servidor, PKCS#8 (0600)
 * ├── server.crt.pem    certificado do servidor seguido da CA (cadeia para o SSL bundle)
 * └── pki.properties    hostname, IP e faixa usados na emissão (para renovar sem repetir argumentos)
 * </pre>
 */
public final class PkiStore {

    public static final String CA_KEY = "ca.key.pem";
    public static final String CA_CERT_PEM = "ca.crt.pem";
    public static final String CA_CERT_DER = "ca.crt";
    public static final String SERVER_KEY = "server.key.pem";
    public static final String SERVER_CERT = "server.crt.pem";
    public static final String METADATA = "pki.properties";

    private static final Set<PosixFilePermission> OWNER_ONLY_FILE = PosixFilePermissions.fromString("rw-------");
    private static final Set<PosixFilePermission> OWNER_ONLY_DIR = PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> PUBLIC_FILE = PosixFilePermissions.fromString("rw-r--r--");

    private final Path directory;

    public PkiStore(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    public Path directory() {
        return directory;
    }

    public Path resolve(String file) {
        return directory.resolve(file);
    }

    public boolean hasCa() {
        return Files.isRegularFile(resolve(CA_KEY)) && Files.isRegularFile(resolve(CA_CERT_PEM));
    }

    public boolean hasServerCertificate() {
        return Files.isRegularFile(resolve(SERVER_KEY)) && Files.isRegularFile(resolve(SERVER_CERT));
    }

    /** Cria o diretório com permissão 0700 quando o sistema de arquivos é POSIX. */
    public void createDirectory() throws IOException {
        Files.createDirectories(directory);
        setPermissions(directory, OWNER_ONLY_DIR);
    }

    public void writeCa(PrivateKey key, X509Certificate certificate, Metadata metadata)
            throws IOException, GeneralSecurityException {
        writeSecret(CA_KEY, toPem("PRIVATE KEY", key.getEncoded()));
        writePublic(CA_CERT_PEM, toPem("CERTIFICATE", certificate.getEncoded()));
        writePublic(CA_CERT_DER, certificate.getEncoded());
        writeMetadata(metadata);
    }

    public void writeServer(PrivateKey key, X509Certificate certificate, X509Certificate caCertificate)
            throws IOException, GeneralSecurityException {
        writeSecret(SERVER_KEY, toPem("PRIVATE KEY", key.getEncoded()));
        byte[] chain = (new String(toPem("CERTIFICATE", certificate.getEncoded()), StandardCharsets.US_ASCII)
                + new String(toPem("CERTIFICATE", caCertificate.getEncoded()), StandardCharsets.US_ASCII))
                .getBytes(StandardCharsets.US_ASCII);
        writePublic(SERVER_CERT, chain);
    }

    public void writeMetadata(Metadata metadata) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("hostname", metadata.hostname());
        properties.setProperty("ip", metadata.ipAddress());
        properties.setProperty("network", metadata.network());
        var buffer = new java.io.ByteArrayOutputStream();
        properties.store(buffer, "Papinho LAN PKI");
        writePublic(METADATA, buffer.toByteArray());
    }

    public Metadata readMetadata() throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(resolve(METADATA))) {
            properties.load(input);
        }
        return new Metadata(properties.getProperty("hostname"), properties.getProperty("ip"),
                properties.getProperty("network"));
    }

    public X509Certificate readCaCertificate() throws IOException, GeneralSecurityException {
        return readCertificates(CA_CERT_PEM).getFirst();
    }

    public List<X509Certificate> readCertificates(String file) throws IOException, GeneralSecurityException {
        try (InputStream input = Files.newInputStream(resolve(file))) {
            return CertificateFactory.getInstance("X.509").generateCertificates(input).stream()
                    .map(X509Certificate.class::cast)
                    .toList();
        }
    }

    public PrivateKey readPrivateKey(String file) throws IOException, GeneralSecurityException {
        String pem = Files.readString(resolve(file), StandardCharsets.US_ASCII);
        String base64 = pem.replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "").replaceAll("\\s", "");
        return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
    }

    /** Impressão digital SHA-256 do certificado DER, em hexadecimal maiúsculo separado por dois-pontos. */
    public static String sha256Fingerprint(X509Certificate certificate) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
            return HexFormat.ofDelimiter(":").withUpperCase().formatHex(digest);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    /** Converte bytes DER de um certificado em {@link X509Certificate}. */
    public static X509Certificate parseCertificate(byte[] der) {
        try {
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(der));
        } catch (CertificateException exception) {
            throw new IllegalArgumentException("Invalid certificate", exception);
        }
    }

    static byte[] toPem(String type, byte[] der) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der);
        return ("-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n")
                .getBytes(StandardCharsets.US_ASCII);
    }

    /**
     * Grava um segredo criando o arquivo já com 0600 e só então trocando-o de lugar: a chave nunca existe
     * em disco com permissão mais aberta, nem por um instante.
     */
    private void writeSecret(String file, byte[] content) throws IOException {
        Path target = resolve(file);
        Path temporary = directory.resolve("." + file + ".tmp");
        Files.deleteIfExists(temporary);
        if (isPosix()) {
            Files.createFile(temporary, PosixFilePermissions.asFileAttribute(OWNER_ONLY_FILE));
        } else {
            Files.createFile(temporary);
        }
        Files.write(temporary, content, StandardOpenOption.TRUNCATE_EXISTING);
        move(temporary, target);
        setPermissions(target, OWNER_ONLY_FILE);
    }

    private void writePublic(String file, byte[] content) throws IOException {
        Path target = resolve(file);
        Path temporary = directory.resolve("." + file + ".tmp");
        Files.write(temporary, content);
        move(temporary, target);
        setPermissions(target, PUBLIC_FILE);
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (FileAlreadyExistsException | java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private boolean isPosix() {
        return Files.getFileAttributeView(directory, PosixFileAttributeView.class) != null;
    }

    private void setPermissions(Path path, Set<PosixFilePermission> permissions) throws IOException {
        if (isPosix()) {
            Files.setPosixFilePermissions(path, permissions);
        }
    }

    /**
     * Parâmetros usados na emissão.
     *
     * @param hostname hostname da instância na LAN
     * @param ipAddress IPv4 do servidor
     * @param network faixa CIDR permitida pela CA
     */
    public record Metadata(String hostname, String ipAddress, String network) {
    }
}
