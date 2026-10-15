package io.github.labdev2025cmd.papinho.messaging;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Histórico dos avanços de entrega e leitura (MS-02). Uma linha por avanço do ponteiro, não por mensagem:
 * "quando a mensagem {@code seq} foi lida" é o primeiro avanço que a cobriu.
 */
@Component
class ReceiptLog {

    enum Kind {
        DELIVERED, READ
    }

    private final JdbcTemplate jdbc;

    ReceiptLog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void record(UUID conversationId, UUID userId, Kind kind, long upToSeq, Instant at) {
        jdbc.update("insert into conversation_receipts (conversation_id, user_id, kind, up_to_seq, at) "
                + "values (?, ?, ?, ?, ?) on conflict do nothing", conversationId, userId, kind.name(), upToSeq,
                at.atOffset(ZoneOffset.UTC));
    }

    /** Quando cada participante recebeu ({@code kind}) a mensagem {@code seq}, por usuário. */
    Map<UUID, Instant> coveredAt(UUID conversationId, Kind kind, long seq) {
        Map<UUID, Instant> result = new HashMap<>();
        jdbc.query("select user_id, min(at) as at from conversation_receipts where conversation_id = ? "
                + "and kind = ? and up_to_seq >= ? group by user_id", row -> {
                    @Nullable OffsetDateTime at = row.getObject("at", OffsetDateTime.class);
                    if (at != null) {
                        result.put(row.getObject("user_id", UUID.class), at.toInstant());
                    }
                }, conversationId, kind.name(), seq);
        return result;
    }
}
