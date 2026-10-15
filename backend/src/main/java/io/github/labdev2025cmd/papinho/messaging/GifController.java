package io.github.labdev2025cmd.papinho.messaging;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.labdev2025cmd.papinho.media.MediaView;
import io.github.labdev2025cmd.papinho.shared.security.CurrentUser;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** GIFs por provedor externo opcional (MS-10); com o provedor desligado, tudo responde 503. */
@RestController
@RequestMapping("/api/v1/gifs")
class GifController {

    private static final String KEY = "[0-9a-f-]{36}";

    private final GifService gifs;

    GifController(GifService gifs) {
        this.gifs = gifs;
    }

    @GetMapping("/status")
    Status status() {
        return new Status(gifs.enabled());
    }

    @GetMapping
    List<GifService.GifView> search(@RequestParam(defaultValue = "") @Size(max = 100) String q, Locale locale) {
        return gifs.search(CurrentUser.id(), q, locale.toLanguageTag().replace('-', '_'));
    }

    @GetMapping("/{key}/preview")
    ResponseEntity<byte[]> preview(@PathVariable @Pattern(regexp = KEY) String key) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_GIF)
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(30)).cachePrivate()).body(gifs.preview(key));
    }

    /** Guarda o GIF escolhido como mídia de quem enviou; a mensagem segue com o {@code attachmentId}. */
    @PostMapping("/{key}/attach")
    MediaView attach(@PathVariable @Pattern(regexp = KEY) String key) throws IOException {
        return gifs.attach(CurrentUser.id(), key);
    }

    record Status(boolean enabled) {
    }
}
