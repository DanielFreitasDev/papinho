package io.github.labdev2025cmd.papinho.messaging;

import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import io.github.labdev2025cmd.papinho.privacy.PrivacyEvents;

/**
 * Quando alguém muda a confirmação de leitura global (PV-06), as conversas individuais dela mudam para os dois
 * lados: cada participante recebe o aviso para buscar a conversa de novo.
 */
@Component
class PrivacyReactions {

    private final NamedParameterJdbcTemplate jdbc;
    private final MessagingNotifier notifier;

    PrivacyReactions(NamedParameterJdbcTemplate jdbc, MessagingNotifier notifier) {
        this.jdbc = jdbc;
        this.notifier = notifier;
    }

    @TransactionalEventListener
    void on(PrivacyEvents.ReadReceiptsChanged event) {
        jdbc.query("""
                select c.id, m.user_id from conversation_members me
                  join conversations c on c.id = me.conversation_id and c.kind = 'DIRECT'
                  join conversation_members m on m.conversation_id = c.id and m.state in ('ACTIVE', 'REQUEST')
                 where me.user_id = :userId
                """, new MapSqlParameterSource("userId", event.userId()), row -> {
                    UUID conversationId = row.getObject("id", UUID.class);
                    notifier.send(Set.of(row.getObject("user_id", UUID.class)),
                            new MessagingNotifier.ConversationEvent("conversation", conversationId));
                });
    }
}
