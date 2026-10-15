package io.github.labdev2025cmd.papinho.messaging;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/** Tira do servidor os arquivos de visualização única já abertos por todos ou com mais de 14 dias (MS-08). */
@Component
class ViewOnceCleanupJob {

    private static final int BATCH = 200;

    private final MessageService messages;

    ViewOnceCleanupJob(MessageService messages) {
        this.messages = messages;
    }

    @Scheduled(fixedDelayString = "${app.messaging.view-once-check:2m}", initialDelayString = "${app.messaging.view-once-check:2m}")
    @SchedulerLock(name = "view-once-cleanup", lockAtMostFor = "PT5M")
    void purge() {
        for (int round = 0; round < 20 && messages.purgeViewOnce(BATCH) == BATCH; round++) {
            // continua até um lote incompleto
        }
    }
}
