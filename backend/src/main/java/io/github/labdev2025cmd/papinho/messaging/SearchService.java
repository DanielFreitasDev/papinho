package io.github.labdev2025cmd.papinho.messaging;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Busca de mensagens (GR-06): global, nas conversas que posso ler (sem as trancadas fechadas), e dentro de uma
 * conversa com filtros. Conversas e contatos são filtrados no cliente, que já tem a lista inteira.
 */
@Service
public class SearchService {

    static final int MIN_QUERY = 2;
    static final int MAX_QUERY = 100;
    static final int MAX_RESULTS = 50;

    private final MessageSearch search;
    private final ConversationService conversations;
    private final MessageService messages;
    private final Clock clock;

    SearchService(MessageSearch search, ConversationService conversations, MessageService messages, Clock clock) {
        this.search = search;
        this.conversations = conversations;
        this.messages = messages;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<MessageService.MessageView> global(UUID me, String query, int limit) {
        String text = validate(query);
        List<UUID> ids = search.global(me, text, conversations.hiddenLocked(me), clock.instant(),
                Math.clamp(limit, 1, MAX_RESULTS));
        return messages.views(me, ids);
    }

    /** Busca dentro da conversa; trancada exige o token de desbloqueio (PV-03). */
    @Transactional(readOnly = true)
    public MessageService.HistoryResponse inConversation(UUID me, UUID conversationId, MessageSearch.Filters filters,
            @org.jspecify.annotations.Nullable Long beforeSeq, int limit) {
        conversations.requireReadable(me, conversationId);
        String query = filters.query();
        MessageSearch.Filters checked = new MessageSearch.Filters(
                query == null || query.isBlank() ? null : validate(query), filters.senderId(), filters.from(),
                filters.to(), filters.type());
        int size = Math.clamp(limit, 1, MAX_RESULTS);
        List<UUID> ids = search.inConversation(me, conversationId, checked, clock.instant(),
                beforeSeq == null ? Long.MAX_VALUE : beforeSeq, size + 1);
        boolean hasMore = ids.size() > size;
        return new MessageService.HistoryResponse(messages.views(me, hasMore ? ids.subList(0, size) : ids), 0,
                hasMore);
    }

    private static String validate(String query) {
        String text = query.strip();
        if (text.length() < MIN_QUERY || text.length() > MAX_QUERY) {
            throw new MessagingExceptions.InvalidMessageException("search query length");
        }
        return text;
    }
}
