package io.github.labdev2025cmd.papinho.media;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * Preenche o número de páginas e a prévia dos PDFs enviados antes de eles serem lidos no upload (MD-01), ou que
 * ficaram para depois com a análise ocupada. Lotes pequenos (cada PDF sobe um processo à parte), lendo o arquivo
 * pela porta de armazenamento (vale também para S3) fora de transação. PDF que não abre fica com nulo e não volta a
 * ser lido; armazenamento fora do ar ou análise ocupada deixam o resto do lote para a próxima rodada.
 */
@Component
class PdfPagesBackfillJob {

    private static final Logger log = LoggerFactory.getLogger(PdfPagesBackfillJob.class);
    private static final int BATCH = 10;

    private final MediaFileRepository files;
    private final FileStorage storage;
    private final PdfPreview previews;
    private final TransactionTemplate transactions;

    PdfPagesBackfillJob(MediaFileRepository files, FileStorage storage, PdfPreview previews,
            TransactionTemplate transactions) {
        this.files = files;
        this.storage = storage;
        this.previews = previews;
        this.transactions = transactions;
    }

    @Scheduled(fixedDelayString = "${app.media.pages-backfill-check:1h}",
            initialDelayString = "${app.media.pages-backfill-delay:2m}")
    @SchedulerLock(name = "media-pages-backfill", lockAtMostFor = "PT30M")
    void fill() {
        for (int round = 0; round < 100 && fillBatch() == BATCH; round++) {
            // continua até um lote incompleto
        }
    }

    /** Analisa até {@value #BATCH} PDFs pendentes; devolve quantos resolveu. */
    int fillBatch() {
        List<MediaFile> pending = transactions.execute(status -> files.findPagesPending(PageRequest.of(0, BATCH)));
        int resolved = 0;
        for (MediaFile file : pending) {
            PdfPreview.Result result;
            try {
                result = analyze(file);
                if (!result.ran()) {
                    break;
                }
                if (result.page() != null) {
                    storage.write(MediaFile.thumbnailKey(file.id()), new ByteArrayInputStream(result.page().jpeg()));
                }
            } catch (IOException unavailable) {
                log.warn("Storage unavailable while reading PDF pages: {}", unavailable.toString());
                break;
            }
            boolean kept = Boolean.TRUE.equals(transactions.execute(status -> files.findById(file.id())
                    .map(found -> {
                        found.pagesRead(result);
                        return true;
                    }).orElse(false)));
            if (!kept && result.page() != null) {
                // Apagado durante a análise: a prévia gravada agora não tem mais dono.
                removeThumbnail(file);
            }
            resolved++;
        }
        return resolved;
    }

    /** Prévia do PDF guardado; nada quando ele é grande demais ou já não existe. */
    private PdfPreview.Result analyze(MediaFile file) throws IOException {
        if (file.size() > PdfPreview.MAX_SIZE) {
            return PdfPreview.Result.NOTHING;
        }
        Optional<InputStream> content = storage.read(MediaFile.contentKey(file.id()));
        if (content.isEmpty()) {
            return PdfPreview.Result.NOTHING;
        }
        try (InputStream in = content.get()) {
            return previews.analyze(file.id(), in);
        }
    }

    private void removeThumbnail(MediaFile file) {
        try {
            storage.delete(MediaFile.thumbnailKey(file.id()));
        } catch (IOException failed) {
            log.warn("Could not remove the PDF preview of deleted media {}: {}", file.id(), failed.toString());
        }
    }
}
