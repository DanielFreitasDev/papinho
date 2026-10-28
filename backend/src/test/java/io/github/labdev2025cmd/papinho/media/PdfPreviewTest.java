package io.github.labdev2025cmd.papinho.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.labdev2025cmd.papinho.support.Pdfs;

/**
 * Prévia de PDF num processo à parte (MD-01): páginas e primeira página dos PDFs gerados aqui, e o que não abre,
 * estoura a memória ou demora sai sem prévia, sem exceção e sem levar o servidor (este processo) junto.
 */
class PdfPreviewTest {

    @TempDir
    Path dir;

    @Test
    @DisplayName("PDF de 3 páginas: conta as páginas e desenha a primeira, na largura das miniaturas e com a proporção dela")
    void shouldCountPagesAndRenderTheFirst() throws IOException {
        PdfPreview.Result result = previews().analyze(UUID.randomUUID(), upload(Pdfs.document(3)));

        assertThat(result.ran()).isTrue();
        assertThat(result.pages()).isEqualTo(3);
        PdfPreview.Page page = result.page();
        assertThat(page).isNotNull();
        assertThat(page.width()).isEqualTo(PhotoPipeline.THUMBNAIL_SIDE);
        assertThat(page.height()).as("A4 em pé: 480 × 841,89 / 595,28").isEqualTo(679);
        assertThat(page.thumbhash()).isNotBlank();
        BufferedImage jpeg = ImageIO.read(new ByteArrayInputStream(page.jpeg()));
        assertThat(jpeg.getWidth()).isEqualTo(480);
        assertThat(jpeg.getHeight()).isEqualTo(679);
        assertThat(luminance(jpeg.getRGB(240, 100))).as("metade de cima preta: é a primeira página").isLessThan(40);
        assertThat(luminance(jpeg.getRGB(240, 600))).as("metade de baixo branca").isGreaterThan(215);
        assertThat(leftovers()).as("nada do processo fica na pasta dos uploads").isEmpty();
    }

    @Test
    @DisplayName("página mais comprida que 1:2 mostra só o topo, sem desenhar uma imagem enorme")
    void shouldCropVeryTallPages() throws IOException {
        PdfPreview.Page page = previews().analyze(UUID.randomUUID(), upload(Pdfs.page(200, 2000))).page();

        assertThat(page).isNotNull();
        assertThat(page.width()).isEqualTo(480);
        assertThat(page.height()).isEqualTo(960);
    }

    @Test
    @DisplayName("PDF com senha de abertura e lixo com nome de PDF: sem páginas, sem prévia e sem exceção")
    void shouldGiveNothingForUnreadablePdfs() throws IOException {
        List<byte[]> unreadable = List.of(Pdfs.withPassword("segredo"),
                "%PDF-1.4 conteúdo qualquer".getBytes(StandardCharsets.UTF_8), garbage());
        for (byte[] content : unreadable) {
            PdfPreview.Result result = previews().analyze(UUID.randomUUID(), upload(content));

            assertThat(result.ran()).isTrue();
            assertThat(result.pages()).isNull();
            assertThat(result.page()).isNull();
        }
        assertThat(leftovers()).isEmpty();
    }

    @Test
    @DisplayName("bomba de descompressão estoura a memória do processo da prévia, não a do servidor; as páginas ficam")
    void shouldSurviveDecompressionBomb() throws IOException {
        // 512 MB de conteúdo num PDF de ~500 KB: o PDFBox descomprime tudo na memória, e o processo tem 256 MB.
        byte[] bomb = Pdfs.decompressionBomb(512L * 1024 * 1024);
        assertThat(bomb.length).isLessThan(2 * 1024 * 1024);

        PdfPreview.Result result = previews().analyze(UUID.randomUUID(), upload(bomb));

        assertThat(result.ran()).isTrue();
        assertThat(result.pages()).as("contadas antes do desenho").isEqualTo(1);
        assertThat(result.page()).isNull();
        assertThat(leftovers()).isEmpty();
    }

    @Test
    @DisplayName("análise que passa do prazo é interrompida e sai sem prévia")
    void shouldStopSlowAnalysis() throws IOException {
        PdfPreview.Result result = previews().analyze(UUID.randomUUID(), upload(Pdfs.document(1)),
                Duration.ofMillis(1));

        assertThat(result.ran()).isTrue();
        assertThat(result.page()).isNull();
        assertThat(leftovers()).isEmpty();
    }

    @Test
    @DisplayName("PDF acima de 100 MB nem é aberto")
    void shouldSkipHugePdfs() throws IOException {
        Path huge = dir.resolve("enorme");
        try (RandomAccessFile file = new RandomAccessFile(huge.toFile(), "rw")) {
            file.write(Pdfs.document(1));
            file.setLength(PdfPreview.MAX_SIZE + 1);
        }

        assertThat(previews().analyze(UUID.randomUUID(), huge)).isEqualTo(PdfPreview.Result.NOTHING);
    }

    @Test
    @DisplayName("JAR aberto por caminho relativo: o processo, que roda em outra pasta, recebe o caminho absoluto")
    void shouldPassAnAbsoluteClassPath() {
        String original = System.getProperty("java.class.path");
        String separator = File.pathSeparator;
        try {
            System.setProperty("java.class.path", "backend/target/papinho.jar" + separator + separator + "lib/*");

            assertThat(PdfPreview.classPath()).isEqualTo(
                    Path.of("backend/target/papinho.jar").toAbsolutePath() + separator
                            + Path.of("lib/*").toAbsolutePath());
        } finally {
            System.setProperty("java.class.path", original);
        }
    }

    private PdfPreview previews() {
        return new PdfPreview(new MediaProperties(dir, Duration.ofHours(24), Duration.ofHours(24),
                new MediaProperties.ClamAv(false, "localhost", 3310, Duration.ofSeconds(60))));
    }

    /** Grava como o upload tus grava: um arquivo sem extensão na pasta dos uploads. */
    private Path upload(byte[] content) throws IOException {
        return Files.write(dir.resolve(UUID.randomUUID().toString()), content);
    }

    /** Pastas e cópias que o processo da prévia deixou para trás. */
    private List<String> leftovers() throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(path -> path.getFileName().toString()).filter(name -> name.startsWith("pdf-")).toList();
        }
    }

    private static byte[] garbage() {
        byte[] data = new byte[8192];
        new java.util.Random(42).nextBytes(data);
        return data;
    }

    private static int luminance(int rgb) {
        return (((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF)) / 3;
    }
}
