package io.github.labdev2025cmd.papinho.messaging;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/** Envia as mensagens agendadas vencidas a cada 15 s, em uma instância por vez (MS-11). */
@Component
class ScheduledMessagesJob {

    private final ScheduledMessageService scheduled;

    ScheduledMessagesJob(ScheduledMessageService scheduled) {
        this.scheduled = scheduled;
    }

    @Scheduled(fixedDelayString = "${app.messaging.scheduled-check:15s}")
    @SchedulerLock(name = "scheduled-messages", lockAtMostFor = "PT5M")
    void dispatch() {
        for (int round = 0; round < 20 && scheduled.dispatchDue(100) == 100; round++) {
            // continua até um lote incompleto
        }
    }
}
