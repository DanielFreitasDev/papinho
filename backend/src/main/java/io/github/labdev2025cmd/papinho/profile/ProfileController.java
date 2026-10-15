package io.github.labdev2025cmd.papinho.profile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.github.labdev2025cmd.papinho.shared.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Perfil do usuário autenticado e fotos de perfil (ID-02). */
@RestController
@RequestMapping("/api/v1")
class ProfileController {

    private final ProfileService service;

    ProfileController(ProfileService service) {
        this.service = service;
    }

    @GetMapping("/profile")
    ProfileResponse own() {
        return ProfileResponse.from(service.own(CurrentUser.id()));
    }

    @PutMapping("/profile")
    ProfileResponse update(@Valid @RequestBody UpdateProfileRequest request) {
        return ProfileResponse.from(service.update(CurrentUser.id(), request.displayName(), request.about()));
    }

    @PutMapping(path = "/profile/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ProfileResponse changePhoto(@RequestPart("photo") MultipartFile photo) throws IOException {
        try (InputStream input = photo.getInputStream()) {
            return ProfileResponse.from(service.changePhoto(CurrentUser.id(), input));
        }
    }

    @DeleteMapping("/profile/photo")
    ProfileResponse removePhoto() {
        return ProfileResponse.from(service.removePhoto(CurrentUser.id()));
    }

    /**
     * Foto de um usuário; 404 tanto quando não existe quanto quando o visitante não pode vê-la (bloqueio,
     * privacidade), para não revelar o motivo. Cache privado curto: a URL do cliente muda com {@code v}.
     */
    @GetMapping("/users/{userId}/photo")
    ResponseEntity<InputStreamResource> photo(@PathVariable UUID userId,
            @RequestParam(defaultValue = "false") boolean thumbnail) {
        return service.photo(CurrentUser.id(), userId, thumbnail)
                .map(stream -> ResponseEntity.ok()
                        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
                        .contentType(MediaType.IMAGE_JPEG)
                        .body(new InputStreamResource(stream)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Alteração do perfil. */
    record UpdateProfileRequest(@NotBlank @Size(max = 64) String displayName, @Nullable @Size(max = 140) String about) {
    }

    /** Perfil devolvido ao cliente. */
    record ProfileResponse(UUID userId, String displayName, @Nullable String about, boolean hasPhoto,
            Instant updatedAt) {

        static ProfileResponse from(ProfileView view) {
            return new ProfileResponse(view.userId(), view.displayName(), view.about(), view.hasPhoto(),
                    view.updatedAt());
        }
    }
}
