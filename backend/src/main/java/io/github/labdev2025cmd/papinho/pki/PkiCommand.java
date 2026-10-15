package io.github.labdev2025cmd.papinho.pki;

import java.io.IOException;
import java.io.PrintStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import io.github.labdev2025cmd.papinho.pki.internal.CertificateAuthority;
import io.github.labdev2025cmd.papinho.pki.internal.PkiStore;

/**
 * Subcomando {@code java -jar papinho.jar pki <ação>} (PK-01). Roda sem Spring e sem banco.
 *
 * <ul>
 *   <li>{@code init}: cria a CA (se ainda não existir) e emite o certificado do servidor;</li>
 *   <li>{@code renew}: reemite só o certificado do servidor com a CA existente;</li>
 *   <li>{@code status}: mostra impressão digital e validade.</li>
 * </ul>
 *
 * <p>É idempotente: rodar {@code init} de novo reaproveita a CA já instalada nos aparelhos e só troca o
 * certificado do servidor. Recriar a CA exige {@code --force-ca}, porque obriga reinstalá-la em todos.</p>
 */
public final class PkiCommand {

    /** Primeiro argumento que desvia o {@code main} para este comando. */
    public static final String NAME = "pki";

    static final String DEFAULT_HOSTNAME = "papinho.lan";
    private static final Pattern HOSTNAME = Pattern.compile(
            "^(?=.{1,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)*$");

    private final PrintStream out;
    private final PrintStream err;
    private final Clock clock;
    private final Map<String, String> environment;
    private final CertificateAuthority authority = new CertificateAuthority();

    PkiCommand(PrintStream out, PrintStream err, Clock clock, Map<String, String> environment) {
        this.out = out;
        this.err = err;
        this.clock = clock;
        this.environment = environment;
    }

    /**
     * Executa o subcomando e devolve o código de saída do processo (0 = sucesso).
     *
     * @param args argumentos após {@code pki}
     */
    public static int run(String[] args, PrintStream out, PrintStream err) {
        return new PkiCommand(out, err, Clock.systemUTC(), System.getenv()).execute(args);
    }

    int execute(String[] args) {
        if (args.length == 0 || "help".equals(args[0]) || "--help".equals(args[0])) {
            printUsage(out);
            return 0;
        }
        try {
            Map<String, String> options = parseOptions(args);
            return switch (args[0]) {
                case "init" -> init(options);
                case "renew" -> renew(options);
                case "status" -> status(options);
                default -> {
                    err.println("Ação desconhecida: " + args[0]);
                    printUsage(err);
                    yield 2;
                }
            };
        } catch (IllegalArgumentException exception) {
            err.println("Erro: " + exception.getMessage());
            return 2;
        } catch (IOException | GeneralSecurityException exception) {
            err.println("Falha ao gerar a PKI: " + exception.getMessage());
            return 1;
        }
    }

    private int init(Map<String, String> options) throws IOException, GeneralSecurityException {
        PkiStore store = new PkiStore(directory(options));
        boolean forceCa = options.containsKey("force-ca");
        Instant now = clock.instant();
        store.createDirectory();

        PkiStore.Metadata metadata;
        X509Certificate caCertificate;
        PrivateKey caKey;
        if (store.hasCa() && !forceCa) {
            metadata = store.readMetadata();
            caCertificate = store.readCaCertificate();
            caKey = store.readPrivateKey(PkiStore.CA_KEY);
            String ip = options.getOrDefault("ip", metadata.ipAddress());
            metadata = new PkiStore.Metadata(metadata.hostname(), ip, metadata.network());
            out.println("CA existente reaproveitada em " + store.directory());
        } else {
            String hostname = hostname(options);
            String ip = options.containsKey("ip") ? options.get("ip") : detectLanAddress()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "não foi possível detectar o IP da LAN; informe --ip 192.168.x.y"));
            String network = CertificateAuthority.normalizeNetwork(options.getOrDefault("network", ip + "/24"));
            metadata = new PkiStore.Metadata(hostname, ip, network);
            KeyPair caKeys = authority.generateKeyPair();
            caCertificate = authority.createCaCertificate(caKeys, hostname, network, now);
            caKey = caKeys.getPrivate();
            store.writeCa(caKey, caCertificate, metadata);
            out.println("CA criada em " + store.directory());
        }
        issueServer(store, metadata, caCertificate, caKey, now);
        printSummary(store, metadata, caCertificate);
        return 0;
    }

    private int renew(Map<String, String> options) throws IOException, GeneralSecurityException {
        PkiStore store = new PkiStore(directory(options));
        if (!store.hasCa()) {
            throw new IllegalArgumentException("nenhuma CA em " + store.directory() + "; rode 'pki init' primeiro");
        }
        PkiStore.Metadata metadata = store.readMetadata();
        String ip = options.getOrDefault("ip", metadata.ipAddress());
        metadata = new PkiStore.Metadata(metadata.hostname(), ip, metadata.network());
        X509Certificate caCertificate = store.readCaCertificate();
        issueServer(store, metadata, caCertificate, store.readPrivateKey(PkiStore.CA_KEY), clock.instant());
        printSummary(store, metadata, caCertificate);
        return 0;
    }

    private int status(Map<String, String> options) throws IOException, GeneralSecurityException {
        PkiStore store = new PkiStore(directory(options));
        if (!store.hasCa()) {
            err.println("Nenhuma CA em " + store.directory());
            return 1;
        }
        printSummary(store, store.readMetadata(), store.readCaCertificate());
        return 0;
    }

    private void issueServer(PkiStore store, PkiStore.Metadata metadata, X509Certificate caCertificate,
            PrivateKey caKey, Instant now) throws IOException, GeneralSecurityException {
        CertificateAuthority.toInetAddress(metadata.ipAddress());
        if (!CertificateAuthority.networkContains(metadata.network(), metadata.ipAddress())) {
            throw new IllegalArgumentException("o IP " + metadata.ipAddress() + " está fora da faixa "
                    + metadata.network() + " permitida pela CA; use --force-ca com --network para recriá-la");
        }
        KeyPair serverKeys = authority.generateKeyPair();
        X509Certificate serverCertificate = authority.issueServerCertificate(serverKeys, caCertificate, caKey,
                metadata.hostname(), metadata.ipAddress(), now);
        store.writeServer(serverKeys.getPrivate(), serverCertificate, caCertificate);
        store.writeMetadata(metadata);
        out.println("Certificado do servidor emitido para " + metadata.hostname() + " e " + metadata.ipAddress()
                + " (válido até " + CertificateAuthority.formatExpiry(serverCertificate) + ")");
    }

    private void printSummary(PkiStore store, PkiStore.Metadata metadata, X509Certificate caCertificate)
            throws IOException, GeneralSecurityException {
        out.println();
        out.println("Hostname ........: " + metadata.hostname());
        out.println("IP do servidor ..: " + metadata.ipAddress());
        out.println("Faixa permitida .: " + metadata.network());
        out.println("CA válida até ...: " + CertificateAuthority.formatExpiry(caCertificate));
        if (store.hasServerCertificate()) {
            X509Certificate server = store.readCertificates(PkiStore.SERVER_CERT).getFirst();
            out.println("Servidor até ....: " + CertificateAuthority.formatExpiry(server));
        }
        out.println("SHA-256 da CA ...: " + PkiStore.sha256Fingerprint(caCertificate));
        out.println();
        out.println("A chave privada da CA está em " + store.resolve(PkiStore.CA_KEY) + " (0600).");
        out.println("Nunca a copie para o repositório nem para outra máquina.");
        out.println("Próximo passo: suba com o perfil 'lan' e abra http://" + metadata.hostname() + "/setup");
    }

    private Path directory(Map<String, String> options) {
        String explicit = options.get("dir");
        if (explicit != null) {
            return Path.of(explicit);
        }
        String fromEnvironment = environment.get("PAPINHO_PKI_DIR");
        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            return Path.of(fromEnvironment);
        }
        return Path.of(System.getProperty("user.home"), ".papinho", "pki");
    }

    private String hostname(Map<String, String> options) {
        String hostname = options.getOrDefault("hostname",
                environment.getOrDefault("PAPINHO_HOSTNAME", DEFAULT_HOSTNAME)).toLowerCase(Locale.ROOT);
        if (!HOSTNAME.matcher(hostname).matches() || hostname.matches("^[0-9.]+$")) {
            // WebAuthn não aceita IP como RP ID: o hostname precisa ser um nome DNS (PK-05).
            throw new IllegalArgumentException("hostname inválido: " + hostname);
        }
        return hostname;
    }

    static Map<String, String> parseOptions(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                throw new IllegalArgumentException("argumento inesperado: " + arg);
            }
            String name = arg.substring(2);
            @Nullable String value = null;
            int equals = name.indexOf('=');
            if (equals >= 0) {
                value = name.substring(equals + 1);
                name = name.substring(0, equals);
            } else if (!"force-ca".equals(name)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("a opção --" + name + " precisa de um valor");
                }
                value = args[++i];
            }
            if (!java.util.Set.of("dir", "hostname", "ip", "network", "force-ca").contains(name)) {
                throw new IllegalArgumentException("opção desconhecida: --" + name);
            }
            options.put(name, value == null ? "true" : value);
        }
        return options;
    }

    /** Primeiro IPv4 privado de uma interface ativa que não seja loopback nem virtual. */
    static Optional<String> detectLanAddress() {
        try {
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!network.isUp() || network.isLoopback() || network.isVirtual()) {
                    continue;
                }
                for (InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        return Optional.of(address.getHostAddress());
                    }
                }
            }
        } catch (SocketException exception) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private static void printUsage(PrintStream stream) {
        stream.println("""
                Uso: java -jar papinho.jar pki <ação> [opções]

                Ações:
                  init     cria a CA da LAN (se não existir) e emite o certificado do servidor
                  renew    reemite apenas o certificado do servidor com a CA existente
                  status   mostra a impressão digital SHA-256 e as validades

                Opções:
                  --dir <caminho>       diretório da PKI (padrão: $PAPINHO_PKI_DIR ou ~/.papinho/pki)
                  --hostname <nome>     hostname da instância (padrão: $PAPINHO_HOSTNAME ou papinho.lan)
                  --ip <ipv4>           IP do servidor na LAN (padrão: detectado)
                  --network <cidr>      faixa de IP permitida pela CA (padrão: IP/24)
                  --force-ca            recria a CA (exige reinstalar a CA em todos os aparelhos)
                """);
    }
}
