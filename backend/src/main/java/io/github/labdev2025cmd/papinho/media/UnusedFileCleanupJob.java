package io.github.labdev2025cmd.papinho.media;

import java.time.Clock;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * Tira do servidor os arquivos enviados que, depois de {@code app.media.unused-file-expiry}, não viraram anexo,
 * figurinha nem foto de grupo (MD-01): sem isso eles ocupavam o disco e a cota de quem enviou para sempre.
 */
@Component
class UnusedFileCleanupJob {

    private static final int BATCH = 200;

    private final MediaService media;
    private final MediaProperties properties;
    private final Clock clock;

    UnusedFileCleanupJob(MediaService media, MediaProperties properties, Clock clock) {
        this.media = media;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.media.unused-file-check:1h}", initialDelayString = "${app.media.unused-file-check:1h}")
    @SchedulerLock(name = "unused-media-cleanup", lockAtMostFor = "PT10M")
    void purge() {
        var cutoff = clock.instant().minus(properties.unusedFileExpiry());
        for (int round = 0; round < 20 && media.purgeUnused(cutoff, BATCH) == BATCH; round++) {
            // continua até um lote incompleto
        }
    }
}
