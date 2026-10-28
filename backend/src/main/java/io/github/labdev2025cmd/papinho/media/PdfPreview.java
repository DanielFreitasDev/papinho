package io.github.labdev2025cmd.papinho.media;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Prévia dos PDFs (MD-01): número de páginas e a primeira página em JPEG, guardada como a miniatura das imagens e com
 * ThumbHash, para a bolha mostrar "5 páginas • PDF • 230 KB" com a página por cima, como no WhatsApp.
 *
 * <p>O PDF vem de qualquer pessoa, e o PDFBox decodifica cada fluxo inteiro na memória, sem teto: um PDF de 1 MB pode
 * virar gigabytes ao descomprimir e, com {@code -XX:+ExitOnOutOfMemoryError}, derrubaria o servidor. Por isso a
 * análise roda num processo Java à parte ({@link PdfPreviewWorker}), um por vez, com heap e prazo limitados: se ele
 * estourar ou travar, só a prévia se perde. Nada aqui lança exceção nem impede o upload.</p>
 */
@Component
class PdfPreview {

    private static final Logger log = LoggerFactory.getLogger(PdfPreview.class);
    /** PDFs maiores não são analisados e seguem como documento comum (o envio aceita até 1 GB). */
    static final long MAX_SIZE = 100L * 1024 * 1024;
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    /** Quanto a análise espera a vez de outra; passado isso, o PDF fica para o job. */
    private static final Duration WAIT = Duration.ofSeconds(10);
    /** Sobra para os PDFs comuns; a "bomba de descompressão" estoura aqui, não no servidor. */
    private static final String HEAP = "-Xmx256m";
    /**
     * Variáveis do servidor que o processo recebe: só caminho, idioma e fuso. Ficam de fora os segredos (senha do
     * banco, chaves) e as opções da JVM do servidor (agentes, outro heap): ele só lê o PDF e grava a prévia.
     */
    private static final Set<String> ENVIRONMENT = Set.of("PATH", "LANG", "LC_ALL", "LC_CTYPE", "TZ");
    /** No JAR do Spring Boot as classes ficam aninhadas: o processo sobe pelo lançador dele. */
    private static final boolean BOOT_JAR = bootJar();

    /** Pasta local dos uploads: a cópia do PDF, a saída de cada processo e o cache de fontes do PDFBox. */
    private final Path workDir;
    /** Um processo por vez (cada um pode ocupar uns 300 MB), na ordem de chegada: o job não fura a fila do upload. */
    private final Semaphore slot = new Semaphore(1, true);

    PdfPreview(MediaProperties properties) {
        this.workDir = properties.uploadDir().toAbsolutePath().normalize();
    }

    /** Analisa o PDF em disco; nunca lança exceção. */
    Result analyze(UUID id, Path pdf) {
        return analyze(id, pdf, TIMEOUT);
    }

    /** A mesma análise para um PDF já guardado (job dos antigos), copiado antes para o disco local. */
    Result analyze(UUID id, InputStream content) throws IOException {
        Files.createDirectories(workDir);
        Path copy = Files.createTempFile(workDir, "pdf-", ".pdf");
        try {
            Files.copy(content, copy, StandardCopyOption.REPLACE_EXISTING);
            return analyze(id, copy);
        } finally {
            Files.deleteIfExists(copy);
        }
    }

    Result analyze(UUID id, Path pdf, Duration timeout) {
        try {
            if (Files.size(pdf) > MAX_SIZE) {
                return Result.NOTHING;
            }
            if (!slot.tryAcquire(WAIT.toMillis(), TimeUnit.MILLISECONDS)) {
                log.info("PDF preview busy, media {} left for the backfill job", id);
                return Result.NOT_RUN;
            }
        } catch (IOException unreadable) {
            return Result.NOTHING;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Result.NOT_RUN;
        }
        Path out = null;
        try {
            Files.createDirectories(workDir);
            out = Files.createTempDirectory(workDir, "pdf-");
            return run(id, pdf, out, timeout);
        } catch (IOException | RuntimeException cannotStart) {
            log.warn("Could not run the PDF preview of media {}: {}", id, cannotStart.toString());
            return Result.NOT_RUN;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Result.NOT_RUN;
        } finally {
            slot.release();
            deleteRecursively(out);
        }
    }

    private Result run(UUID id, Path pdf, Path out, Duration timeout) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command(pdf, out)).directory(out.toFile())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().keySet().retainAll(ENVIRONMENT);
        Process process = builder.start();
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                log.warn("PDF preview of media {} exceeded {} and was stopped", id, timeout);
            } else if (process.exitValue() != 0) {
                // 3 é o estouro de memória (-XX:+ExitOnOutOfMemoryError).
                log.info("PDF preview of media {} ended with exit code {}", id, process.exitValue());
            }
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        }
        return read(out);
    }

    private List<String> command(Path pdf, Path out) {
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(), HEAP,
                // Processo curto: um coletor de uma thread só, compilação rápida e nada em /tmp/hsperfdata.
                "-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1", "-XX:-UsePerfData",
                // Estourou a memória, sai na hora (código 3) em vez de se arrastar.
                "-XX:+ExitOnOutOfMemoryError", "-Djava.awt.headless=true", "-Djava.io.tmpdir=" + out,
                // O PDFBox guarda a lista das fontes do sistema; sem isso, na pasta pessoal.
                "-Dpdfbox.fontcache=" + workDir,
                // A saída é descartada; sem isso, o Logback sem configuração formataria tudo em DEBUG.
                "-Dslf4j.provider=org.slf4j.helpers.NOP_FallbackServiceProvider",
                "-cp", classPath()));
        if (BOOT_JAR) {
            command.add("-Dloader.main=" + PdfPreviewWorker.class.getName());
            command.add("org.springframework.boot.loader.launch.PropertiesLauncher");
        } else {
            command.add(PdfPreviewWorker.class.getName());
        }
        command.add(pdf.toString());
        command.add(out.toString());
        return command;
    }

    /**
     * O classpath do servidor com caminhos absolutos: o processo roda dentro da pasta de saída, e um
     * {@code java -jar backend/target/papinho.jar} deixaria o caminho relativo apontando para lugar nenhum.
     */
    static String classPath() {
        return Stream.of(System.getProperty("java.class.path").split(File.pathSeparator))
                .filter(entry -> !entry.isEmpty())
                .map(entry -> Path.of(entry).toAbsolutePath().normalize().toString())
                .collect(Collectors.joining(File.pathSeparator));
    }

    /** O que o processo publicou; o que não fizer sentido vale como nada. */
    private static Result read(Path out) {
        Path result = out.resolve(PdfPreviewWorker.RESULT);
        try {
            if (!Files.isRegularFile(result)) {
                return Result.NOTHING;
            }
            String[] fields = Files.readString(result).strip().split(" ");
            int pages = Integer.parseInt(fields[0]);
            if (pages < 1) {
                return Result.NOTHING;
            }
            if (fields.length != 4) {
                return new Result(true, pages, null);
            }
            int width = Integer.parseInt(fields[1]);
            int height = Integer.parseInt(fields[2]);
            String thumbhash = fields[3];
            boolean valid = width == PdfPreviewWorker.WIDTH && height >= 1 && height <= PdfPreviewWorker.MAX_HEIGHT
                    && thumbhash.length() <= 64;
            return new Result(true, pages, valid
                    ? new Page(Files.readAllBytes(out.resolve(PdfPreviewWorker.IMAGE)), width, height, thumbhash)
                    : null);
        } catch (IOException | RuntimeException invalid) {
            return Result.NOTHING;
        }
    }

    private static boolean bootJar() {
        ClassLoader loader = PdfPreview.class.getClassLoader();
        return loader != null && loader.getClass().getName().startsWith("org.springframework.boot.loader.");
    }

    private static void deleteRecursively(@Nullable Path dir) {
        if (dir == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (IOException | RuntimeException failed) {
            log.warn("Could not remove the PDF preview folder {}: {}", dir.getFileName(), failed.toString());
        }
    }

    /**
     * Resultado da análise.
     *
     * @param ran falso quando ela nem rodou (outra análise na vez, processo que não sobe): o PDF fica para o job
     * @param pages número de páginas; nulo quando o PDF não abre (senha de abertura, corrompido, grande demais)
     * @param page prévia da primeira página; nula quando não deu para desenhar
     */
    record Result(boolean ran, @Nullable Integer pages, @Nullable Page page) {

        static final Result NOT_RUN = new Result(false, null, null);
        static final Result NOTHING = new Result(true, null, null);
    }

    /** Primeira página em JPEG, com as dimensões da própria prévia e o ThumbHash. */
    record Page(byte[] jpeg, int width, int height, String thumbhash) {
    }
}
