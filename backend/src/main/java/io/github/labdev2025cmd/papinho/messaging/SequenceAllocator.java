package io.github.labdev2025cmd.papinho.messaging;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contador de {@code seq} por conversa (ADR 0010). O UPDATE ... RETURNING trava a linha da conversa até o fim
 * da transação: escritas na mesma conversa ficam em fila e o seq nunca repete nem pula.
 */
@Component
class SequenceAllocator {

    private final JdbcTemplate jdbc;

    SequenceAllocator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    long next(UUID conversationId, Instant now) {
        Long seq = jdbc.queryForObject(
                "update conversations set last_seq = last_seq + 1, updated_at = ? where id = ? returning last_seq",
                Long.class, now.atOffset(ZoneOffset.UTC), conversationId);
        if (seq == null) {
            throw new IllegalStateException("Conversation " + conversationId + " not found");
        }
        return seq;
    }

    /** Trava a linha da conversa sem avançar o seq (checagens antes de gravar, como a idempotência). */
    @Transactional(propagation = Propagation.MANDATORY)
    void lock(UUID conversationId) {
        jdbc.queryForList("select id from conversations where id = ? for update", conversationId);
    }

    /** Trava transacional por chave, para criar a conversa individual de um par uma vez só. */
    @Transactional(propagation = Propagation.MANDATORY)
    void lockKey(String key) {
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?, 0))", key);
    }

    /**
     * Avança o seq para uma alteração (reação, fixar) sem mexer na ordem da lista de conversas, que segue a
     * última mensagem.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    long nextChange(UUID conversationId) {
        Long seq = jdbc.queryForObject("update conversations set last_seq = last_seq + 1 where id = ? returning last_seq",
                Long.class, conversationId);
        if (seq == null) {
            throw new IllegalStateException("Conversation " + conversationId + " not found");
        }
        return seq;
    }

    long current(UUID conversationId) {
        Long seq = jdbc.queryForObject("select last_seq from conversations where id = ?", Long.class,
                conversationId);
        return seq == null ? 0 : seq;
    }
}
