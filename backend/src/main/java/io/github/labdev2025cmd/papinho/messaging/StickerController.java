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

/** Biblioteca de figurinhas da instância e favoritas (MS-10). */
@RestController
@RequestMapping("/api/v1/stickers")
class StickerController {

    private final StickerService stickers;

    StickerController(StickerService stickers) {
        this.stickers = stickers;
    }

    @GetMapping("/packs")
    List<StickerService.PackView> packs() {
        return stickers.packs(CurrentUser.id());
    }

    @PostMapping("/packs")
    @ResponseStatus(HttpStatus.CREATED)
    StickerService.PackView createPack(@Valid @RequestBody PackRequest body) {
        return stickers.createPack(CurrentUser.id(), body.name());
    }

    @DeleteMapping("/packs/{packId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removePack(@PathVariable UUID packId) {
        stickers.removePack(CurrentUser.id(), packId);
    }

    @PostMapping("/packs/{packId}/stickers")
    @ResponseStatus(HttpStatus.CREATED)
    StickerService.StickerView add(@PathVariable UUID packId, @Valid @RequestBody StickerRequest body) {
        return stickers.addSticker(CurrentUser.id(), packId, body.mediaId(), body.emoji());
    }

    @DeleteMapping("/{stickerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable UUID stickerId) {
        stickers.removeSticker(CurrentUser.id(), stickerId);
    }

    @GetMapping("/favorites")
    List<StickerService.StickerView> favorites() {
        return stickers.favorites(CurrentUser.id());
    }

    @PutMapping("/{stickerId}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void favorite(@PathVariable UUID stickerId) {
        stickers.favorite(CurrentUser.id(), stickerId, true);
    }

    @DeleteMapping("/{stickerId}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unfavorite(@PathVariable UUID stickerId) {
        stickers.favorite(CurrentUser.id(), stickerId, false);
    }

    record PackRequest(@NotBlank @Size(max = 64) String name) {
    }

    record StickerRequest(@NotNull UUID mediaId, @Nullable @Size(max = 32) String emoji) {
    }
}
