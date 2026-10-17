package io.github.labdev2025cmd.papinho.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import io.github.labdev2025cmd.papinho.support.Accounts;
import io.github.labdev2025cmd.papinho.support.ApiClient;
import io.github.labdev2025cmd.papinho.support.IntegrationTest;
import io.github.labdev2025cmd.papinho.support.TestSupportConfiguration.ApiClients;
import tools.jackson.databind.JsonNode;

/**
 * Corrida entre a sincronização e um envio (RT-01): uma mensagem confirmada enquanto o {@code afterSeq} lê o
 * banco não pode ficar para trás do {@code lastSeq} devolvido, senão o cliente avança o cursor e a perde.
 */
@IntegrationTest
class SyncRaceIT {

    @Autowired
    private ApiClients clients;

    @Autowired
    private MessageService messageService;

    @MockitoSpyBean
    private SequenceAllocator sequences;

    private final AtomicBoolean armed = new AtomicBoolean(true);
    private UUID anaId;
    private ApiClient bruno;

    @Test
    @DisplayName("mensagem confirmada no meio da sincronização chega na próxima busca pelo cursor (RT-01)")
    void shouldNotSkipMessageCommittedDuringSync() {
        UUID conversation = conversationWithFirstMessage();
        armInterleavedSend(conversation);

        // O cliente aplica o algoritmo de cache.ts: cursor = max(changeSeq) e, sem mais páginas, max(lastSeq).
        List<String> seen = syncFrom(conversation, 1);
        assertThat(armed).as("o envio intercalado aconteceu").isFalse();
        assertThat(seen).as("mensagens vistas pela sincronização").containsExactly("no meio");
    }

    @Test
    @DisplayName("mensagem confirmada enquanto o histórico carrega não fica para trás do lastSeq dele (RT-01)")
    void shouldNotSkipMessageCommittedDuringHistory() {
        UUID conversation = conversationWithFirstMessage();
        armInterleavedSend(conversation);

        JsonNode history = bruno.json(bruno.get(path(conversation)));
        List<String> seen = new ArrayList<>();
        history.get("messages").forEach(message -> seen.add(message.get("body").asString()));
        seen.addAll(syncFrom(conversation, history.get("lastSeq").asLong()));
        assertThat(armed).as("o envio intercalado aconteceu").isFalse();
        assertThat(seen).as("histórico mais sincronização").contains("primeira", "no meio");
    }

    private UUID conversationWithFirstMessage() {
        ApiClient ana = clients.create();
        anaId = UUID.fromString(Accounts.userId(Accounts.register(ana, "ana", null)));
        bruno = clients.create();
        Accounts.register(bruno, "bruno", Accounts.invite(ana));
        var opened = ana.post("/api/v1/conversations/direct", Map.of("username", "bruno"));
        UUID conversation = UUID.fromString(ana.json(opened).get("id").asString());
        send(conversation, "primeira");
        return conversation;
    }

    /** Na próxima leitura do seq atual feita pela thread do teste, outra thread confirma uma mensagem antes. */
    private void armInterleavedSend(UUID conversation) {
        Thread requestThread = Thread.currentThread();
        doAnswer(invocation -> {
            if (Thread.currentThread() == requestThread && armed.compareAndSet(true, false)) {
                CompletableFuture.runAsync(() -> send(conversation, "no meio")).join();
            }
            return invocation.callRealMethod();
        }).when(sequences).current(any());
    }

    private List<String> syncFrom(UUID conversation, long start) {
        List<String> seen = new ArrayList<>();
        long cursor = start;
        for (int round = 0; round < 3; round++) {
            JsonNode page = bruno.json(bruno.get(path(conversation) + "?afterSeq=" + cursor));
            for (JsonNode message : page.get("messages")) {
                seen.add(message.get("body").asString());
                cursor = Math.max(cursor, message.get("changeSeq").asLong());
            }
            if (!page.get("hasMore").asBoolean()) {
                cursor = Math.max(cursor, page.get("lastSeq").asLong());
            }
        }
        return seen;
    }

    private static String path(UUID conversation) {
        return "/api/v1/conversations/" + conversation + "/messages";
    }

    private void send(UUID conversation, String body) {
        messageService.send(anaId, conversation, UUID.randomUUID(), body, null, null,
                MessageService.SendOptions.NONE);
    }
}
