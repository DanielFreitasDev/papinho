package io.github.labdev2025cmd.papinho.pki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * O {@code install-ca.sh} de verdade (PK-04), com o repositório do sistema trocado por comandos falsos que só
 * guardam o arquivo que seria instalado. Precisa de {@code sh}, {@code curl} e {@code openssl}.
 */
class InstallCaScriptTest {

    private static final Pattern BLOCK = Pattern.compile("-----BEGIN ([A-Z ]+)-----");

    @TempDir
    private Path dir;

    private Path stubs;
    private Path installed;

    @BeforeEach
    void setUp() throws IOException {
        assumeTrue(available("sh", "-c", "command -v curl && command -v openssl"), "sh, curl e openssl");
        stubs = Files.createDirectories(dir.resolve("bin"));
        installed = dir.resolve("instalado.pem");
        // root sem sudo; "install" guarda a origem; o resto do repositório e do NSS não faz nada (ou falha).
        stub("id", "echo 0");
        stub("install", "eval src=\\${$(($#-1))}; cp \"$src\" " + installed);
        stub("update-ca-certificates", "exit 0");
        stub("certutil", "case \" $* \" in *\" -L \"*) exit 1 ;; esac");
        Files.createDirectories(dir.resolve("home"));
    }

    @Test
    @DisplayName("impressão digital certa: instala só o certificado conferido")
    void shouldInstallVerifiedCertificate() throws Exception {
        Path ca = certificate("Papinho LAN CA");
        Result result = run(ca, fingerprint(ca));
        assertThat(result.exit()).as(result.output()).isZero();
        assertThat(blocks(installed)).containsExactly("CERTIFICATE");
        assertThat(der(installed)).isEqualTo(der(ca));
    }

    @Test
    @DisplayName("impressão digital errada aborta com 'não confere' e não instala nada")
    void shouldAbortOnWrongFingerprint() throws Exception {
        Path expected = certificate("Papinho LAN CA");
        Path other = certificate("Outra CA");
        Result result = run(other, fingerprint(expected));
        assertThat(result.exit()).isEqualTo(1);
        assertThat(result.output()).contains("não confere").contains("Nada foi instalado");
        assertThat(installed).doesNotExist();
    }

    private Result run(Path served, String fingerprint) throws Exception {
        String script;
        try (InputStream in = getClass().getResourceAsStream("/pki/install-ca.sh")) {
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        script = script.replace("@@CA_URL@@", served.toUri().toString()).replace("@@FINGERPRINT@@", fingerprint)
                .replace("@@HOSTNAME_PORT@@", "papinho.lan").replace("@@HOSTNAME_HTTPS@@", "papinho.lan")
                .replace("@@HOSTNAME@@", "papinho.lan");
        Path file = dir.resolve("install-ca.sh");
        Files.writeString(file, script);
        ProcessBuilder builder = new ProcessBuilder("sh", file.toString(), "--yes").redirectErrorStream(true);
        builder.environment().put("PATH", stubs + ":/usr/bin:/bin");
        builder.environment().put("HOME", dir.resolve("home").toString());
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
        return new Result(process.exitValue(), output);
    }

    private Path certificate(String name) throws Exception {
        Path pem = dir.resolve(name.replace(' ', '-') + ".pem");
        exec("openssl", "req", "-x509", "-newkey", "ec", "-pkeyopt", "ec_paramgen_curve:P-256", "-nodes", "-keyout",
                dir.resolve(name.replace(' ', '-') + ".key").toString(), "-subj", "/CN=" + name, "-days", "30",
                "-out", pem.toString());
        return pem;
    }

    private static String fingerprint(Path pem) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(der(pem)));
    }

    /** DER do primeiro bloco CERTIFICATE do arquivo. */
    private static byte[] der(Path pem) throws IOException {
        String text = Files.readString(pem);
        String body = text.substring(text.indexOf("-----BEGIN CERTIFICATE-----") + 27,
                text.indexOf("-----END CERTIFICATE-----"));
        return Base64.getMimeDecoder().decode(body);
    }

    private static List<String> blocks(Path pem) throws IOException {
        Matcher matcher = BLOCK.matcher(Files.readString(pem));
        List<String> found = new java.util.ArrayList<>();
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    private void stub(String name, String body) throws IOException {
        Path file = stubs.resolve(name);
        Files.writeString(file, "#!/bin/sh\n" + body + "\n");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-xr-x"));
    }

    private static void exec(String... command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor()).as(output).isZero();
    }

    private static boolean available(String... command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor() == 0;
        } catch (IOException | InterruptedException unavailable) {
            return false;
        }
    }

    private record Result(int exit, String output) {
    }
}
