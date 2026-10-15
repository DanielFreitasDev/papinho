package io.github.labdev2025cmd.papinho.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
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
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Listas de transmissão (GR-03) e temporárias (GR-04); enviar é pela rota normal de mensagens da conversa. */
@RestController
@RequestMapping("/api/v1/broadcasts")
class BroadcastController {

    private final BroadcastService broadcasts;

    BroadcastController(BroadcastService broadcasts) {
        this.broadcasts = broadcasts;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ConversationService.ConversationView create(@Valid @RequestBody BroadcastRequest body) {
        return broadcasts.create(CurrentUser.id(), body.name(), body.recipientIds(), body.expiresAt(),
                body.messageTtlSeconds());
    }

    @PutMapping("/{id}")
    ConversationService.ConversationView update(@PathVariable UUID id, @Valid @RequestBody BroadcastRequest body) {
        return broadcasts.update(CurrentUser.id(), id, body.name(), body.recipientIds(), body.expiresAt(),
                body.messageTtlSeconds());
    }

    @GetMapping("/{id}/recipients")
    List<GroupService.MemberView> recipients(@PathVariable UUID id) {
        return broadcasts.recipients(CurrentUser.id(), id);
    }

    record BroadcastRequest(@NotBlank @Size(max = GroupDetails.MAX_NAME) String name,
            @NotEmpty @Size(max = BroadcastService.MAX_RECIPIENTS) List<@NotNull UUID> recipientIds,
            @Nullable Instant expiresAt, @Nullable Integer messageTtlSeconds) {
    }
}
