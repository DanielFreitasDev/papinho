package io.github.labdev2025cmd.papinho.messaging;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
    private final ScheduledMessageService scheduled;

    MessagingController(ConversationService conversations, MessageService messages,
            ScheduledMessageService scheduled) {
        this.conversations = conversations;
        this.messages = messages;
        this.scheduled = scheduled;
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

    /** "Mensagens salvas" (MS-12): abre (ou cria) a conversa consigo mesmo. */
    @PostMapping("/conversations/saved")
    ConversationService.ConversationView openSaved() {
        return conversations.openSelf(CurrentUser.id());
    }

    /** Timer de mensagens temporárias da conversa (MS-07): 86400, 604800, 7776000 ou nulo. */
    @PutMapping("/conversations/{conversationId}/disappearing")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void disappearing(@PathVariable UUID conversationId, @RequestBody DisappearingRequest body) {
        messages.changeDisappearing(CurrentUser.id(), conversationId, body.seconds());
    }

    @GetMapping("/messaging/preferences")
    MessageService.Preferences preferences() {
        return messages.preferences(CurrentUser.id());
    }

    @PutMapping("/messaging/preferences")
    MessageService.Preferences changePreferences(@RequestBody MessageService.Preferences body) {
        return messages.changePreferences(CurrentUser.id(), body.defaultDisappearingSeconds());
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
                body.body(), body.replyToId(), body.attachmentId(), new MessageService.SendOptions(
                        Boolean.TRUE.equals(body.viewOnce()), Boolean.TRUE.equals(body.silent()), body.stickerId()));
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.message());
    }

    /** Rascunho sincronizado entre os meus dispositivos (MS-11); texto vazio apaga. */
    @PutMapping("/conversations/{conversationId}/draft")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void draft(@PathVariable UUID conversationId, @Valid @RequestBody DraftRequest body) {
        conversations.saveDraft(CurrentUser.id(), conversationId, body.body());
    }

    /** Agenda uma mensagem de texto (MS-11). */
    @PostMapping("/conversations/{conversationId}/scheduled")
    @ResponseStatus(HttpStatus.CREATED)
    ScheduledMessageService.ScheduledView schedule(@PathVariable UUID conversationId,
            @Valid @RequestBody ScheduleRequest body) {
        return scheduled.schedule(CurrentUser.id(), conversationId, body.body(), body.sendAt(),
                Boolean.TRUE.equals(body.silent()));
    }

    @GetMapping("/conversations/{conversationId}/scheduled")
    List<ScheduledMessageService.ScheduledView> scheduled(@PathVariable UUID conversationId) {
        return scheduled.list(CurrentUser.id(), conversationId);
    }

    @DeleteMapping("/scheduled/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancelScheduled(@PathVariable UUID id) {
        scheduled.cancel(CurrentUser.id(), id);
    }

    /** Cria uma enquete (MS-09); 201 quando criou, 200 quando o {@code clientMessageId} já existia. */
    @PostMapping("/conversations/{conversationId}/polls")
    ResponseEntity<MessageService.MessageView> createPoll(@PathVariable UUID conversationId,
            @Valid @RequestBody CreatePollRequest body) {
        MessageService.SendResult result = messages.createPoll(CurrentUser.id(), conversationId,
                body.clientMessageId(), body.question(), body.options(), body.multiple(), body.anonymous(),
                body.closesAt());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.message());
    }

    /** Vota numa enquete; lista vazia tira o voto (MS-09). */
    @PutMapping("/messages/{messageId}/vote")
    MessageService.MessageView vote(@PathVariable UUID messageId, @Valid @RequestBody VoteRequest body) {
        return messages.vote(CurrentUser.id(), messageId, body.optionIds());
    }

    /** Galeria (MD-03): mídia, links, documentos ou áudios da conversa, do mais novo para o mais antigo. */
    @GetMapping("/conversations/{conversationId}/gallery")
    MessageService.HistoryResponse gallery(@PathVariable UUID conversationId,
            @RequestParam MessageService.GalleryTab tab, @RequestParam @Nullable @Min(1) Long beforeSeq,
            @RequestParam(defaultValue = "30") @Min(1) @Max(MessageService.MAX_PAGE) int limit) {
        return messages.gallery(CurrentUser.id(), conversationId, tab, beforeSeq, limit);
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

    /** Emite o token único para abrir uma mensagem de visualização única (MS-08). */
    @PostMapping("/messages/{messageId}/view-once")
    MessageService.ViewOnceGrant openViewOnce(@PathVariable UUID messageId) {
        return messages.openViewOnce(CurrentUser.id(), messageId);
    }

    /** Conteúdo de visualização única pelo token: nunca vai para cache (navegador, proxy ou service worker). */
    @GetMapping("/view-once/{token}")
    ResponseEntity<org.springframework.core.io.Resource> viewOnceContent(
            @PathVariable @jakarta.validation.constraints.Pattern(regexp = "[A-Za-z0-9_-]{20,64}") String token)
            throws java.io.IOException {
        io.github.labdev2025cmd.papinho.media.MediaService.Content content = messages.viewOnceContent(
                CurrentUser.id(), token);
        return ResponseEntity.ok().contentType(org.springframework.http.MediaType.parseMediaType(
                content.contentType())).cacheControl(org.springframework.http.CacheControl.noStore())
                .header(org.springframework.http.HttpHeaders.PRAGMA, "no-cache")
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(content.resource());
    }

    /** Edita o texto de uma mensagem própria dentro da janela (MS-05). */
    @PatchMapping("/messages/{messageId}")
    MessageService.MessageView edit(@PathVariable UUID messageId, @Valid @RequestBody EditMessageRequest body) {
        return messages.edit(CurrentUser.id(), messageId, body.body());
    }

    /** Apaga para todos (MS-06); devolve o aviso "Esta mensagem foi apagada". */
    @DeleteMapping("/messages/{messageId}")
    MessageService.MessageView deleteForEveryone(@PathVariable UUID messageId) {
        return messages.deleteForEveryone(CurrentUser.id(), messageId);
    }

    /** Apaga para mim (MS-06). */
    @PostMapping("/messages/hide")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void hide(@Valid @RequestBody HideRequest body) {
        messages.hide(CurrentUser.id(), body.messageIds());
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

    record CreatePollRequest(@NotNull UUID clientMessageId,
            @NotBlank @Size(max = MessageService.MAX_POLL_QUESTION) String question,
            @NotNull @Size(min = 2, max = MessageService.MAX_POLL_OPTIONS) List<@NotBlank @Size(
                    max = MessageService.MAX_POLL_OPTION) String> options,
            boolean multiple, boolean anonymous, java.time.@Nullable Instant closesAt) {
    }

    record VoteRequest(@NotNull @Size(max = MessageService.MAX_POLL_OPTIONS) List<@NotNull UUID> optionIds) {
    }

    record DraftRequest(@NotNull @Size(max = Message.MAX_BODY_LENGTH) String body) {
    }

    record ScheduleRequest(@NotBlank @Size(max = Message.MAX_BODY_LENGTH) String body,
            java.time.@NotNull Instant sendAt, @Nullable Boolean silent) {
    }

    record DisappearingRequest(@Nullable Integer seconds) {
    }

    record PointerRequest(@PositiveOrZero long seq) {
    }

    record SendMessageRequest(@NotNull UUID clientMessageId, @NotNull @Size(max = Message.MAX_BODY_LENGTH) String body,
            @Nullable UUID replyToId, @Nullable UUID attachmentId, @Nullable Boolean viewOnce,
            @Nullable Boolean silent, @Nullable UUID stickerId) {
    }

    record ForwardRequest(@NotEmpty @Size(max = 20) List<@NotNull UUID> messageIds,
            @NotEmpty @Size(max = 5) List<@NotNull UUID> conversationIds) {
    }

    record EditMessageRequest(@NotNull @Size(max = Message.MAX_BODY_LENGTH) String body) {
    }

    record HideRequest(@NotEmpty @Size(max = 100) List<@NotNull UUID> messageIds) {
    }

    record ReactionRequest(@NotBlank @Size(max = 32) String emoji) {
    }
}
