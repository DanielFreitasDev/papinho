package io.github.labdev2025cmd.papinho.messaging;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.labdev2025cmd.papinho.messaging.MessagingExceptions.InvalidMessageException;
import io.github.labdev2025cmd.papinho.messaging.MessagingExceptions.NotFoundException;
import io.github.labdev2025cmd.papinho.shared.error.ApiException;
import io.github.labdev2025cmd.papinho.shared.id.Uuids;

/**
 * Mensagens agendadas (MS-11): texto que sai na hora marcada pelo mesmo caminho do envio normal, com as mesmas
 * regras (bloqueio, "só admins enviam"); se na hora não puder sair, fica marcada como falha para quem agendou.
 */
@Service
public class ScheduledMessageService {

    /** Agendar até um ano à frente. */
    static final Duration MAX_AHEAD = Duration.ofDays(365);

    private final NamedParameterJdbcTemplate jdbc;
    private final ConversationService conversations;
    private final MessageService messages;
    private final TransactionTemplate eachSend;
    private final Clock clock;

    ScheduledMessageService(NamedParameterJdbcTemplate jdbc, ConversationService conversations,
            MessageService messages, PlatformTransactionManager transactions, Clock clock) {
        this.jdbc = jdbc;
        this.conversations = conversations;
        this.messages = messages;
        this.eachSend = new TransactionTemplate(transactions);
        this.eachSend.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    @Transactional
    public ScheduledView schedule(UUID me, UUID conversationId, String body, Instant sendAt, boolean silent) {
        conversations.requireSendable(me, conversationId);
        Instant now = clock.instant();
        if (body.isBlank() || body.length() > Message.MAX_BODY_LENGTH) {
            throw new InvalidMessageException("body");
        }
        if (!sendAt.isAfter(now) || sendAt.isAfter(now.plus(MAX_AHEAD))) {
            throw new InvalidMessageException("schedule time");
        }
        UUID id = Uuids.v7();
        jdbc.update("insert into scheduled_messages (id, conversation_id, sender_id, client_message_id, body, silent, "
                + "send_at, created_at) values (:id, :conversationId, :me, :clientMessageId, :body, :silent, "
                + ":sendAt, :now)", new MapSqlParameterSource("id", id).addValue("conversationId", conversationId)
                .addValue("me", me).addValue("clientMessageId", Uuids.v7()).addValue("body", body)
                .addValue("silent", silent).addValue("sendAt", sendAt.atOffset(ZoneOffset.UTC))
                .addValue("now", now.atOffset(ZoneOffset.UTC)));
        return new ScheduledView(id, conversationId, body, sendAt, silent, null);
    }

    /** As minhas agendadas nesta conversa, pela hora de envio. */
    @Transactional(readOnly = true)
    public List<ScheduledView> list(UUID me, UUID conversationId) {
        conversations.requireMember(me, conversationId);
        return jdbc.query("select id, conversation_id, body, send_at, silent, failed_at from scheduled_messages "
                + "where sender_id = :me and conversation_id = :conversationId order by send_at",
                new MapSqlParameterSource("me", me).addValue("conversationId", conversationId),
                (rs, index) -> new ScheduledView(rs.getObject("id", UUID.class),
                        rs.getObject("conversation_id", UUID.class), rs.getString("body"),
                        rs.getObject("send_at", OffsetDateTime.class).toInstant(), rs.getBoolean("silent"),
                        failedAt(rs.getObject("failed_at", OffsetDateTime.class))));
    }

    /** Cancela (só quem agendou; 404 para os outros). */
    @Transactional
    public void cancel(UUID me, UUID id) {
        if (jdbc.update("delete from scheduled_messages where id = :id and sender_id = :me",
                new MapSqlParameterSource("id", id).addValue("me", me)) == 0) {
            throw new NotFoundException();
        }
    }

    /** Envia as vencidas, cada uma na própria transação; devolve quantas processou. */
    public int dispatchDue(int limit) {
        List<Due> due = jdbc.query("select id, conversation_id, sender_id, client_message_id, body, silent "
                + "from scheduled_messages where failed_at is null and send_at <= :now order by send_at limit :limit",
                new MapSqlParameterSource("now", clock.instant().atOffset(ZoneOffset.UTC)).addValue("limit", limit),
                (rs, index) -> new Due(rs.getObject("id", UUID.class), rs.getObject("conversation_id", UUID.class),
                        rs.getObject("sender_id", UUID.class), rs.getObject("client_message_id", UUID.class),
                        rs.getString("body"), rs.getBoolean("silent")));
        for (Due item : due) {
            try {
                eachSend.executeWithoutResult(status -> {
                    messages.send(item.senderId(), item.conversationId(), item.clientMessageId(), item.body(), null,
                            null, new MessageService.SendOptions(false, item.silent()));
                    jdbc.update("delete from scheduled_messages where id = :id",
                            new MapSqlParameterSource("id", item.id()));
                });
            } catch (ApiException refused) {
                jdbc.update("update scheduled_messages set failed_at = :now where id = :id",
                        new MapSqlParameterSource("id", item.id())
                                .addValue("now", clock.instant().atOffset(ZoneOffset.UTC)));
            }
        }
        return due.size();
    }

    private static @Nullable Instant failedAt(@Nullable OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private record Due(UUID id, UUID conversationId, UUID senderId, UUID clientMessageId, String body,
            boolean silent) {
    }

    /** @param failedAt quando o envio na hora marcada falhou (nulo = ainda vai sair) */
    public record ScheduledView(UUID id, UUID conversationId, String body, Instant sendAt, boolean silent,
            @Nullable Instant failedAt) {
    }
}
