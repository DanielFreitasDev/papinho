package io.github.labdev2025cmd.papinho.media;

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

import io.github.labdev2025cmd.papinho.media.internal.audio.AudioDuration;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * Preenche a duração dos áudios enviados antes de ela ser lida no upload (AU-02), em lotes, lendo cada arquivo
 * pela porta de armazenamento (vale também para S3). A leitura fica fora de transação, para um arquivo lento não
 * segurar conexão do banco. Arquivo que não diz a duração fica com nulo e não volta a ser lido; armazenamento
 * fora do ar deixa o lote para a próxima rodada.
 */
@Component
class AudioDurationBackfillJob {

    private static final Logger log = LoggerFactory.getLogger(AudioDurationBackfillJob.class);
    private static final int BATCH = 50;

    private final MediaFileRepository files;
    private final FileStorage storage;
    private final TransactionTemplate transactions;

    AudioDurationBackfillJob(MediaFileRepository files, FileStorage storage, TransactionTemplate transactions) {
        this.files = files;
        this.storage = storage;
        this.transactions = transactions;
    }

    @Scheduled(fixedDelayString = "${app.media.duration-backfill-check:1h}",
            initialDelayString = "${app.media.duration-backfill-delay:2m}")
    @SchedulerLock(name = "media-duration-backfill", lockAtMostFor = "PT30M")
    void fill() {
        for (int round = 0; round < 100 && fillBatch() == BATCH; round++) {
            // continua até um lote incompleto
        }
    }

    /** Lê a duração de até {@value #BATCH} áudios pendentes; devolve quantos resolveu. */
    int fillBatch() {
        List<MediaFile> pending = transactions.execute(status -> files.findDurationPending(PageRequest.of(0, BATCH)));
        int resolved = 0;
        for (MediaFile file : pending) {
            Optional<Long> duration;
            try {
                duration = read(file);
            } catch (IOException unavailable) {
                log.warn("Storage unavailable while reading audio durations: {}", unavailable.toString());
                break;
            }
            transactions.executeWithoutResult(status -> files.findById(file.id())
                    .ifPresent(found -> found.durationRead(duration.orElse(null))));
            resolved++;
        }
        return resolved;
    }

    /** Duração do arquivo guardado; vazio quando ele não diz ou já não existe. */
    private Optional<Long> read(MediaFile file) throws IOException {
        Optional<InputStream> content = storage.read(MediaFile.contentKey(file.id()));
        if (content.isEmpty()) {
            return Optional.empty();
        }
        try (InputStream in = content.get()) {
            return Optional.ofNullable(AudioDuration.millis(in, file.contentType()));
        }
    }
}
