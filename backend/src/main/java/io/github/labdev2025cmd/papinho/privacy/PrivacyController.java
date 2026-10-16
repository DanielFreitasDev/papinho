package io.github.labdev2025cmd.papinho.privacy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.labdev2025cmd.papinho.shared.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Configurações de privacidade, bloqueio de tela e conversas trancadas (PV-01..PV-08). */
@RestController
@RequestMapping("/api/v1/privacy")
class PrivacyController {

    private final PrivacyService privacy;
    private final ScreenLockService screenLock;
    private final ConversationLocks locks;

    PrivacyController(PrivacyService privacy, ScreenLockService screenLock, ConversationLocks locks) {
        this.privacy = privacy;
        this.screenLock = screenLock;
        this.locks = locks;
    }

    @GetMapping
    PrivacyView get() {
        UUID me = CurrentUser.id();
        return new PrivacyView(privacy.settings(me), privacy.exceptions(me));
    }

    @PatchMapping
    PrivacyView update(@RequestBody PrivacyService.SettingsChange body) {
        UUID me = CurrentUser.id();
        return new PrivacyView(privacy.update(me, body), privacy.exceptions(me));
    }

    /** Troca a lista "Meus contatos exceto…" de um item. */
    @PutMapping("/exceptions/{item}")
    List<UUID> replaceExceptions(@PathVariable PrivacyItem item, @Valid @RequestBody ExceptionsRequest body) {
        return privacy.replaceExceptions(CurrentUser.id(), item, body.userIds());
    }

    @GetMapping("/screen-lock")
    ScreenLockService.ScreenLockView screenLock() {
        return screenLock.view(CurrentUser.id());
    }

    @PutMapping("/screen-lock")
    ScreenLockService.ScreenLockView saveScreenLock(@RequestBody ScreenLockService.ScreenLockChange body) {
        return screenLock.save(CurrentUser.id(), body);
    }

    @DeleteMapping("/screen-lock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeScreenLock(@RequestBody SecretRequest body) {
        screenLock.remove(CurrentUser.id(), body.secret());
    }

    /** Confere o PIN ou a senha do bloqueio de tela; 204 quando confere. */
    @PostMapping("/screen-lock/unlock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unlockScreen(@RequestBody SecretRequest body) {
        screenLock.unlock(CurrentUser.id(), body.secret());
    }

    /** Tranca uma conversa com um código (o mesmo código de outra conversa vira código compartilhado). */
    @PutMapping("/locked-conversations/{conversationId}")
    ConversationLocks.Unlocked lock(@PathVariable UUID conversationId, @Valid @RequestBody CodeRequest body) {
        return locks.lock(CurrentUser.id(), conversationId, body.code());
    }

    /** Destranca de vez; exige o token de desbloqueio que cobre a conversa. */
    @DeleteMapping("/locked-conversations/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unlockPermanently(@PathVariable UUID conversationId) {
        locks.unlockPermanently(CurrentUser.id(), conversationId);
    }

    /** Abre a pasta de conversas trancadas com um código: devolve o token para o cabeçalho de desbloqueio. */
    @PostMapping("/locked-conversations/unlock")
    ConversationLocks.Unlocked unlock(@Valid @RequestBody CodeRequest body) {
        return locks.unlock(CurrentUser.id(), body.code());
    }

    /** Fecha a pasta: o token atual deixa de valer. */
    @PostMapping("/locked-conversations/lock-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void lockAll() {
        locks.forget();
    }

    /** Quantas conversas trancadas existem (para mostrar a pasta), sem dizer quais. */
    @GetMapping("/locked-conversations")
    Map<String, Integer> count() {
        return Map.of("count", locks.lockedConversations(CurrentUser.id()).size());
    }

    record PrivacyView(PrivacyService.Settings settings, Map<PrivacyItem, List<UUID>> exceptions) {
    }

    record ExceptionsRequest(@NotNull @Size(max = 1000) List<UUID> userIds) {
    }

    record SecretRequest(@Nullable String secret) {
    }

    record CodeRequest(@NotNull @Size(min = 1, max = 64) String code) {
    }
}
