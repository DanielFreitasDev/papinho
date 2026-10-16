package io.github.labdev2025cmd.papinho.messaging;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.labdev2025cmd.papinho.shared.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Organização da lista de conversas (GR-05): arquivar, fixar, silenciar, favoritar, pastas, limpar e apagar. */
@RestController
@RequestMapping("/api/v1")
class ChatListController {

    private final ChatListService chatList;

    ChatListController(ChatListService chatList) {
        this.chatList = chatList;
    }

    @PutMapping("/conversations/{conversationId}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archive(@PathVariable UUID conversationId) {
        chatList.archive(CurrentUser.id(), conversationId, true);
    }

    @DeleteMapping("/conversations/{conversationId}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unarchive(@PathVariable UUID conversationId) {
        chatList.archive(CurrentUser.id(), conversationId, false);
    }

    /** Fixa no topo; com três já fixadas dá 409 {@code conversation-pin-limit}. */
    @PutMapping("/conversations/{conversationId}/pin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void pin(@PathVariable UUID conversationId) {
        chatList.pin(CurrentUser.id(), conversationId, true);
    }

    @DeleteMapping("/conversations/{conversationId}/pin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unpin(@PathVariable UUID conversationId) {
        chatList.pin(CurrentUser.id(), conversationId, false);
    }

    @PutMapping("/conversations/{conversationId}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void favorite(@PathVariable UUID conversationId) {
        chatList.favorite(CurrentUser.id(), conversationId, true);
    }

    @DeleteMapping("/conversations/{conversationId}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unfavorite(@PathVariable UUID conversationId) {
        chatList.favorite(CurrentUser.id(), conversationId, false);
    }

    /** Silencia por 8 h, 1 semana ou sempre; {@code duration} nulo volta a notificar. */
    @PutMapping("/conversations/{conversationId}/mute")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void mute(@PathVariable UUID conversationId, @RequestBody MuteRequest body) {
        chatList.mute(CurrentUser.id(), conversationId, body.duration(), Boolean.TRUE.equals(body.mentions()));
    }

    /** Limpa as mensagens só para mim (por padrão mantém as favoritas). */
    @PostMapping("/conversations/{conversationId}/clear")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void clear(@PathVariable UUID conversationId, @RequestBody(required = false) @Nullable ClearRequest body) {
        chatList.clear(CurrentUser.id(), conversationId, body == null || !Boolean.FALSE.equals(body.keepStarred()));
    }

    /** Apaga a conversa da minha lista até chegar mensagem nova. */
    @DeleteMapping("/conversations/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable UUID conversationId) {
        chatList.remove(CurrentUser.id(), conversationId);
    }

    @GetMapping("/folders")
    List<ChatListService.FolderView> folders() {
        return chatList.folders(CurrentUser.id());
    }

    @PostMapping("/folders")
    @ResponseStatus(HttpStatus.CREATED)
    ChatListService.FolderView createFolder(@Valid @RequestBody FolderRequest body) {
        return chatList.createFolder(CurrentUser.id(), body.name(), body.conversationIds());
    }

    @PutMapping("/folders/{folderId}")
    ChatListService.FolderView updateFolder(@PathVariable UUID folderId, @Valid @RequestBody FolderRequest body) {
        return chatList.updateFolder(CurrentUser.id(), folderId, body.name(), body.conversationIds());
    }

    @DeleteMapping("/folders/{folderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteFolder(@PathVariable UUID folderId) {
        chatList.deleteFolder(CurrentUser.id(), folderId);
    }

    @PutMapping("/folders/{folderId}/conversations/{conversationId}")
    ChatListService.FolderView addToFolder(@PathVariable UUID folderId, @PathVariable UUID conversationId) {
        return chatList.toggleFolderItem(CurrentUser.id(), folderId, conversationId, true);
    }

    @DeleteMapping("/folders/{folderId}/conversations/{conversationId}")
    ChatListService.FolderView removeFromFolder(@PathVariable UUID folderId, @PathVariable UUID conversationId) {
        return chatList.toggleFolderItem(CurrentUser.id(), folderId, conversationId, false);
    }

    record MuteRequest(ChatListService.@Nullable MuteDuration duration, @Nullable Boolean mentions) {
    }

    record ClearRequest(@Nullable Boolean keepStarred) {
    }

    record FolderRequest(@NotBlank @Size(max = 40) String name,
            @NotNull @Size(max = 500) List<@NotNull UUID> conversationIds) {
    }
}
