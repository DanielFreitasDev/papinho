package io.github.labdev2025cmd.papinho.messaging;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/** Apaga as mensagens temporárias vencidas a cada minuto, em uma instância por vez (MS-07, ShedLock). */
@Component
class DisappearingMessagesJob {

    private static final int BATCH = 500;

    private final MessageService messages;

    DisappearingMessagesJob(MessageService messages) {
        this.messages = messages;
    }

    @Scheduled(fixedDelayString = "${app.messaging.expiry-check:60s}", initialDelayString = "${app.messaging.expiry-check:60s}")
    @SchedulerLock(name = "disappearing-messages", lockAtMostFor = "PT5M")
    void purge() {
        // Lotes em transações separadas; para quando um lote vem incompleto.
        for (int round = 0; round < 20 && messages.purgeExpired(BATCH) == BATCH; round++) {
            // continua
        }
    }
}
