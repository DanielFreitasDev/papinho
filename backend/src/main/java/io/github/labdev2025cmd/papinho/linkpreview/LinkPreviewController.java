package io.github.labdev2025cmd.papinho.linkpreview;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.labdev2025cmd.papinho.shared.security.CurrentUser;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Prévia de links (MD-04, MD-05). */
@RestController
@RequestMapping("/api/v1/link-previews")
class LinkPreviewController {

    private final LinkPreviewService previews;

    LinkPreviewController(LinkPreviewService previews) {
        this.previews = previews;
    }

    @GetMapping
    LinkPreviewService.PreviewView preview(@RequestParam @Size(max = 2048) String url) {
        return previews.preview(CurrentUser.id(), url);
    }

    @GetMapping("/{hash}/image")
    ResponseEntity<byte[]> image(@PathVariable @Pattern(regexp = "[0-9a-f]{64}") String hash) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate()).body(previews.image(hash));
    }
}
