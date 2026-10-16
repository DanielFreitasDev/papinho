package io.github.labdev2025cmd.papinho.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.labdev2025cmd.papinho.shared.security.CurrentUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Busca de mensagens (GR-06). */
@RestController
@RequestMapping("/api/v1")
class SearchController {

    private final SearchService search;

    SearchController(SearchService search) {
        this.search = search;
    }

    /** Busca global nas minhas conversas, da mensagem mais nova para a mais antiga. */
    @GetMapping("/search/messages")
    List<MessageService.MessageView> global(@RequestParam String q,
            @RequestParam(defaultValue = "30") @Min(1) @Max(SearchService.MAX_RESULTS) int limit) {
        return search.global(CurrentUser.id(), q, limit);
    }

    /** Busca dentro da conversa por texto, remetente, período ({@code from} inclusive, {@code to} exclusive) e tipo. */
    @GetMapping("/conversations/{conversationId}/search")
    MessageService.HistoryResponse inConversation(@PathVariable UUID conversationId,
            @RequestParam @Nullable String q, @RequestParam @Nullable UUID senderId,
            @RequestParam @Nullable Instant from, @RequestParam @Nullable Instant to,
            @RequestParam MessageService.@Nullable GalleryTab type, @RequestParam @Nullable @Min(1) Long beforeSeq,
            @RequestParam(defaultValue = "30") @Min(1) @Max(SearchService.MAX_RESULTS) int limit) {
        return search.inConversation(CurrentUser.id(), conversationId,
                new MessageSearch.Filters(q, senderId, from, to, type), beforeSeq, limit);
    }
}
