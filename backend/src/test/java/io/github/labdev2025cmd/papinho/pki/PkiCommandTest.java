package io.github.labdev2025cmd.papinho.pki;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.labdev2025cmd.papinho.pki.internal.PkiStore;

class PkiCommandTest {

    @TempDir
    Path tempDir;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private int run(String... args) {
        var command = new PkiCommand(new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8),
                Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC), Map.of());
        return command.execute(args);
    }

    @Test
    @DisplayName("pki init cria CA e servidor com chaves privadas em 0600")
    void shouldCreateFilesWithOwnerOnlyKeys() throws Exception {
        Path dir = tempDir.resolve("pki");

        int status = run("init", "--dir", dir.toString(), "--ip", "192.168.0.10");

        assertThat(status).as(err.toString()).isZero();
        assertThat(dir.resolve(PkiStore.CA_KEY)).exists();
        assertThat(dir.resolve(PkiStore.CA_CERT_DER)).exists();
        assertThat(dir.resolve(PkiStore.SERVER_CERT)).exists();
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(dir.resolve(PkiStore.CA_KEY))))
                .isEqualTo("rw-------");
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(dir.resolve(PkiStore.SERVER_KEY))))
                .isEqualTo("rw-------");
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(dir))).isEqualTo("rwx------");
        assertThat(out.toString()).contains("SHA-256 da CA").contains("papinho.lan").contains("192.168.0.0/24");
        var chain = new PkiStore(dir).readCertificates(PkiStore.SERVER_CERT);
        assertThat(chain).hasSize(2);
    }

    @Test
    @DisplayName("pki init de novo reaproveita a CA e só reemite o servidor")
    void shouldReuseExistingCa() throws Exception {
        Path dir = tempDir.resolve("pki");
        run("init", "--dir", dir.toString(), "--ip", "192.168.0.10");
        String caBefore = Files.readString(dir.resolve(PkiStore.CA_CERT_PEM));
        String serverBefore = Files.readString(dir.resolve(PkiStore.SERVER_CERT));

        int status = run("init", "--dir", dir.toString(), "--ip", "192.168.0.11");

        assertThat(status).isZero();
        assertThat(Files.readString(dir.resolve(PkiStore.CA_CERT_PEM))).isEqualTo(caBefore);
        assertThat(Files.readString(dir.resolve(PkiStore.SERVER_CERT))).isNotEqualTo(serverBefore);
        assertThat(out.toString()).contains("CA existente reaproveitada").contains("192.168.0.11");
    }

    @Test
    @DisplayName("IP fora da faixa da CA é recusado")
    void shouldRejectIpOutsideCaNetwork() {
        Path dir = tempDir.resolve("pki");
        run("init", "--dir", dir.toString(), "--ip", "192.168.0.10");

        int status = run("renew", "--dir", dir.toString(), "--ip", "10.0.0.5");

        assertThat(status).isEqualTo(2);
        assertThat(err.toString()).contains("fora da faixa");
    }

    @Test
    @DisplayName("hostname que é IP é recusado, porque passkeys exigem nome DNS")
    void shouldRejectIpAsHostname() {
        int status = run("init", "--dir", tempDir.toString(), "--ip", "192.168.0.10", "--hostname", "192.168.0.10");

        assertThat(status).isEqualTo(2);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("192.168.0.10").contains("papinho.lan")
                .contains("WebAuthn").contains("Hostname na rede");
    }

    @Test
    @DisplayName("com locale POSIX, a mensagem sai em UTF-8, sem acentos trocados por '?'")
    void shouldWriteUtf8WithPosixLocale() throws Exception {
        ProcessBuilder builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), "io.github.labdev2025cmd.papinho.PapinhoApplication",
                "pki", "init", "--dir", tempDir.toString(), "--ip", "192.168.0.10", "--hostname", "192.168.0.10");
        builder.environment().put("LANG", "C");
        builder.environment().put("LC_ALL", "C");
        builder.environment().remove("JAVA_TOOL_OPTIONS");
        Process process = builder.start();
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor()).isEqualTo(2);
        assertThat(stderr).contains("não").doesNotContain("n?o");
    }

    @Test
    @DisplayName("opção desconhecida é recusada com código 2")
    void shouldRejectUnknownOption() {
        assertThat(run("init", "--bogus", "x")).isEqualTo(2);
    }
}
