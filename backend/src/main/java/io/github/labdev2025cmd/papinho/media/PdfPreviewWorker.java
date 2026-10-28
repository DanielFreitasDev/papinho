package io.github.labdev2025cmd.papinho.media;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.io.RandomAccessRead;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.jspecify.annotations.Nullable;

/**
 * Processo filho da prévia de PDF ({@link PdfPreview}): abre o PDF com o PDFBox, publica o número de páginas e desenha
 * a primeira página. Uso: {@code PdfPreviewWorker <pdf> <pasta de saída>}; grava {@value #IMAGE} e, por último,
 * {@value #RESULT} com "páginas [largura altura thumbhash]". A contagem sai antes do desenho, para sobreviver a um
 * desenho que estoure a memória ou o prazo.
 */
final class PdfPreviewWorker {

    static final String RESULT = "result.txt";
    static final String IMAGE = "page.jpg";
    /** Largura da prévia: a das miniaturas de imagem, para ocupar a bolha do mesmo jeito. */
    static final int WIDTH = PhotoPipeline.THUMBNAIL_SIDE;
    /** Página mais comprida que 1:2 (extrato, cupom) mostra só o topo. */
    static final int MAX_HEIGHT = 2 * WIDTH;
    /** Fluxos que o PDFBox cria (os decifrados, nos PDFs protegidos) passam disso para arquivo temporário... */
    private static final long SCRATCH_MEMORY = 16L * 1024 * 1024;
    /** ...até este teto, somando memória e disco. */
    private static final long SCRATCH_LIMIT = 512L * 1024 * 1024;
    /** No Linux, o quanto o kernel prefere este processo quando a memória acaba (1000: o primeiro da fila). */
    private static final Path OOM_SCORE_ADJ = Path.of("/proc/self/oom_score_adj");

    private PdfPreviewWorker() {
    }

    public static void main(String[] args) {
        offerToOomKiller();
        Path pdf = Path.of(args[0]);
        Path out = Path.of(args[1]);
        ImageIO.setUseCache(false);
        MemoryUsageSetting scratch = MemoryUsageSetting.setupMixed(SCRATCH_MEMORY, SCRATCH_LIMIT)
                .setTempDir(out.toFile());
        try (RandomAccessRead source = new RandomAccessReadBufferedFile(pdf.toFile());
                PDDocument document = Loader.loadPDF(source, scratch.streamCache)) {
            int pages = document.getNumberOfPages();
            if (pages < 1) {
                return;
            }
            publish(out, Integer.toString(pages));
            BufferedImage page = firstPage(document);
            if (page != null) {
                Files.write(out.resolve(IMAGE), ImageProcessing.jpeg(page));
                publish(out, pages + " " + page.getWidth() + " " + page.getHeight() + " "
                        + PhotoPipeline.thumbhash(page));
            }
        } catch (IOException | RuntimeException unreadable) {
            // PDF com senha de abertura, corrompido ou que o PDFBox não desenha: vale o que já foi publicado.
        }
    }

    /**
     * Primeira página com {@value #WIDTH} px de largura sobre fundo branco, já girada; a altura segue a proporção da
     * página até {@value #MAX_HEIGHT} px. Nula quando a página não tem tamanho que dê para desenhar.
     */
    static @Nullable BufferedImage firstPage(PDDocument document) throws IOException {
        PDPage page = document.getPage(0);
        PDRectangle box = page.getCropBox();
        boolean turned = page.getRotation() % 180 != 0;
        float width = turned ? box.getHeight() : box.getWidth();
        float height = turned ? box.getWidth() : box.getHeight();
        if (!(width >= 1 && height >= 1 && Float.isFinite(width) && Float.isFinite(height))) {
            return null;
        }
        float scale = WIDTH / width;
        int rows = Math.clamp(Math.round((double) height * scale), 1, MAX_HEIGHT);
        BufferedImage image = new BufferedImage(WIDTH, rows, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setBackground(Color.WHITE);
            graphics.clearRect(0, 0, WIDTH, rows);
            PDFRenderer renderer = new PDFRenderer(document);
            // Imagens maiores que a prévia são lidas já reduzidas.
            renderer.setSubsamplingAllowed(true);
            renderer.renderPageToGraphics(0, graphics, scale);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    /** Grava o resultado de uma vez (renomeando), para o servidor nunca ler um arquivo pela metade. */
    /**
     * O filho divide o limite de memória do servidor (o cgroup do contêiner ou o {@code MemoryMax} da unit): se ela
     * acabar, o kernel encerra este processo primeiro, e o PDF perde só a prévia. Subir o próprio valor não pede
     * privilégio.
     */
    private static void offerToOomKiller() {
        try {
            Files.writeString(OOM_SCORE_ADJ, "1000", StandardOpenOption.WRITE);
        } catch (IOException | RuntimeException unsupported) {
            // Fora do Linux, ou sem /proc: vale só o teto do heap.
        }
    }

    private static void publish(Path out, String result) throws IOException {
        Path partial = out.resolve(RESULT + ".partial");
        Files.writeString(partial, result);
        Files.move(partial, out.resolve(RESULT), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }
}
