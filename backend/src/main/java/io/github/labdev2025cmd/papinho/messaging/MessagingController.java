package io.github.labdev2025cmd.papinho.messaging;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.labdev2025cmd.papinho.shared.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Conversas e mensagens (MS-02, MS-04, MS-14, RT-01, RT-02, CT-02). */
@RestController
@RequestMapping("/api/v1")
class MessagingController {

    private final ConversationService conversations;
    private final MessageService messages;

    MessagingController(ConversationService conversations, MessageService messages) {
        this.conversations = conversations;
        this.messages = messages;
    }

    /** Conversas; com {@code requests=true}, as solicitações de mensagem pendentes. */
    @GetMapping("/conversations")
    List<ConversationService.ConversationView> list(@RequestParam(defaultValue = "false") boolean requests) {
        return conversations.list(CurrentUser.id(), requests);
    }

    /** Abre (ou devolve) a conversa individual com alguém. */
    @PostMapping("/conversations/direct")
    ConversationService.ConversationView openDirect(@Valid @RequestBody OpenDirectRequest body) {
        return conversations.openDirect(CurrentUser.id(), body.username());
    }

    @GetMapping("/conversations/{conversationId}")
    ConversationService.ConversationView get(@PathVariable UUID conversationId) {
        return conversations.view(CurrentUser.id(), conversationId);
    }

    @PostMapping("/conversations/{conversationId}/accept")
    ConversationService.ConversationView accept(@PathVariable UUID conversationId) {
        return conversations.accept(CurrentUser.id(), conversationId);
    }

    @PostMapping("/conversations/{conversationId}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void decline(@PathVariable UUID conversationId) {
        conversations.decline(CurrentUser.id(), conversationId);
    }

    @PutMapping("/conversations/{conversationId}/delivered")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delivered(@PathVariable UUID conversationId, @Valid @RequestBody PointerRequest body) {
        conversations.markDelivered(CurrentUser.id(), conversationId, body.seq());
    }

    @PutMapping("/conversations/{conversationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void read(@PathVariable UUID conversationId, @Valid @RequestBody PointerRequest body) {
        conversations.markRead(CurrentUser.id(), conversationId, body.seq());
    }

    @PutMapping("/conversations/{conversationId}/unread")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markUnread(@PathVariable UUID conversationId) {
        conversations.markUnread(CurrentUser.id(), conversationId);
    }

    /**
     * Com {@code afterSeq}: alterações desde esse seq, em ordem (sincronização). Sem ele: página de histórico
     * antes de {@code beforeSeq} (ou as mais recentes).
     */
    @GetMapping("/conversations/{conversationId}/messages")
    Object messages(@PathVariable UUID conversationId, @RequestParam @Nullable @PositiveOrZero Long afterSeq,
            @RequestParam @Nullable @Min(1) Long beforeSeq,
            @RequestParam(defaultValue = "50") @Min(1) @Max(MessageService.MAX_SYNC) int limit) {
        if (afterSeq != null) {
            return messages.sync(CurrentUser.id(), conversationId, afterSeq, limit);
        }
        return messages.history(CurrentUser.id(), conversationId, beforeSeq, limit);
    }

    /** Envia; 201 quando criou, 200 quando o {@code clientMessageId} já existia (RT-02). */
    @PostMapping("/conversations/{conversationId}/messages")
    ResponseEntity<MessageService.MessageView> send(@PathVariable UUID conversationId,
            @Valid @RequestBody SendMessageRequest body) {
        MessageService.SendResult result = messages.send(CurrentUser.id(), conversationId, body.clientMessageId(),
                body.body(), body.replyToId(), body.attachmentId());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.message());
    }

    @GetMapping("/conversations/{conversationId}/pinned")
    List<MessageService.MessageView> pinned(@PathVariable UUID conversationId) {
        return messages.pinned(CurrentUser.id(), conversationId);
    }

    @PostMapping("/messages/forward")
    @ResponseStatus(HttpStatus.CREATED)
    List<MessageService.MessageView> forward(@Valid @RequestBody ForwardRequest body) {
        return messages.forward(CurrentUser.id(), body.messageIds(), body.conversationIds());
    }

    @PutMapping("/messages/{messageId}/reaction")
    MessageService.MessageView react(@PathVariable UUID messageId, @Valid @RequestBody ReactionRequest body) {
        return messages.react(CurrentUser.id(), messageId, body.emoji());
    }

    @DeleteMapping("/messages/{messageId}/reaction")
    MessageService.MessageView unreact(@PathVariable UUID messageId) {
        return messages.react(CurrentUser.id(), messageId, null);
    }

    @PutMapping("/messages/{messageId}/pin")
    MessageService.MessageView pin(@PathVariable UUID messageId) {
        return messages.pin(CurrentUser.id(), messageId, true);
    }

    @DeleteMapping("/messages/{messageId}/pin")
    MessageService.MessageView unpin(@PathVariable UUID messageId) {
        return messages.pin(CurrentUser.id(), messageId, false);
    }

    @PutMapping("/messages/{messageId}/star")
    MessageService.MessageView star(@PathVariable UUID messageId) {
        return messages.star(CurrentUser.id(), messageId, true);
    }

    @DeleteMapping("/messages/{messageId}/star")
    MessageService.MessageView unstar(@PathVariable UUID messageId) {
        return messages.star(CurrentUser.id(), messageId, false);
    }

    @GetMapping("/messages/starred")
    List<MessageService.MessageView> starred() {
        return messages.starred(CurrentUser.id());
    }

    /** "Dados da mensagem" (MS-02); só para quem enviou. */
    @GetMapping("/messages/{messageId}/info")
    MessageService.MessageInfo info(@PathVariable UUID messageId) {
        return messages.info(CurrentUser.id(), messageId);
    }

    record OpenDirectRequest(@NotBlank @Size(max = 64) String username) {
    }

    record PointerRequest(@PositiveOrZero long seq) {
    }

    record SendMessageRequest(@NotNull UUID clientMessageId, @NotNull @Size(max = Message.MAX_BODY_LENGTH) String body,
            @Nullable UUID replyToId, @Nullable UUID attachmentId) {
    }

    record ForwardRequest(@NotEmpty @Size(max = 20) List<@NotNull UUID> messageIds,
            @NotEmpty @Size(max = 5) List<@NotNull UUID> conversationIds) {
    }

    record ReactionRequest(@NotBlank @Size(max = 32) String emoji) {
    }
}
