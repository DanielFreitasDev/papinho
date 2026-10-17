package io.github.labdev2025cmd.papinho.profile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.labdev2025cmd.papinho.media.FileStorage;
import io.github.labdev2025cmd.papinho.media.ImageProcessing;
import io.github.labdev2025cmd.papinho.shared.error.ApiException;
import io.github.labdev2025cmd.papinho.shared.id.Uuids;

/** Casos de uso do perfil (ID-02). */
@Service
public class ProfileService {

    /** Lado da foto grande e da miniatura, em pixels. */
    static final int PHOTO_SIZE = 512;
    static final int THUMB_SIZE = 96;

    private final ProfileRepository repository;
    private final FileStorage storage;
    private final List<ProfileVisibilityPolicy> policies;
    private final Clock clock;

    ProfileService(ProfileRepository repository, FileStorage storage, List<ProfileVisibilityPolicy> policies,
            Clock clock) {
        this.repository = repository;
        this.storage = storage;
        this.policies = policies;
        this.clock = clock;
    }

    /** Cria o perfil no cadastro; chamado na mesma transação que cria a conta. */
    @Transactional
    public void create(UUID userId, String displayName) {
        repository.save(new Profile(userId, normalizeDisplayName(displayName), clock.instant()));
    }

    /** Perfil do próprio usuário, sem filtros. */
    @Transactional(readOnly = true)
    public ProfileView own(UUID userId) {
        Profile profile = load(userId);
        return new ProfileView(userId, profile.displayName(), profile.about(), profile.photoKey() != null,
                profile.updatedAt());
    }

    /** Perfil de {@code owner} como {@code viewer} pode vê-lo. */
    @Transactional(readOnly = true)
    public Optional<ProfileView> view(UUID viewer, UUID owner) {
        return repository.findById(owner).map(profile -> toView(viewer, profile));
    }

    /** Perfis de vários usuários de uma vez (listas de contatos e conversas), já filtrados para {@code viewer}. */
    @Transactional(readOnly = true)
    public Map<UUID, ProfileView> viewAll(UUID viewer, Collection<UUID> owners) {
        if (owners.isEmpty()) {
            return Map.of();
        }
        return repository.findByUserIdIn(owners).stream().map(profile -> toView(viewer, profile))
                .collect(Collectors.toMap(ProfileView::userId, Function.identity()));
    }

    @Transactional
    public ProfileView update(UUID userId, String displayName, @Nullable String about) {
        Profile profile = load(userId);
        String normalizedAbout = about == null || about.isBlank() ? null : about.strip();
        profile.update(normalizeDisplayName(displayName), normalizedAbout, clock.instant());
        return own(userId);
    }

    /**
     * Troca a foto. O cliente já envia o recorte; aqui a imagem é recodificada (sem EXIF) em 512 px e 96 px.
     */
    @Transactional
    public ProfileView changePhoto(UUID userId, InputStream image) {
        Profile profile = load(userId);
        try {
            byte[] large = ImageProcessing.squareJpeg(image, PHOTO_SIZE);
            byte[] thumb = ImageProcessing.thumbnail(large, THUMB_SIZE);
            String key = "profiles/" + userId + "/" + Uuids.v7();
            storage.write(key, new ByteArrayInputStream(large));
            storage.write(key + "-thumb", new ByteArrayInputStream(thumb));
            String previous = profile.photoKey();
            profile.changePhoto(key, clock.instant());
            deleteQuietly(previous);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to store profile photo", exception);
        }
        return own(userId);
    }

    @Transactional
    public ProfileView removePhoto(UUID userId) {
        Profile profile = load(userId);
        String previous = profile.photoKey();
        profile.changePhoto(null, clock.instant());
        deleteQuietly(previous);
        return own(userId);
    }

    /** Foto (ou miniatura) de {@code owner}, se existir e {@code viewer} puder vê-la. */
    @Transactional(readOnly = true)
    public Optional<InputStream> photo(UUID viewer, UUID owner, boolean thumbnail) {
        Optional<Profile> profile = repository.findById(owner);
        if (profile.isEmpty() || profile.get().photoKey() == null || !canSeePhoto(viewer, owner)) {
            return Optional.empty();
        }
        try {
            return storage.read(profile.get().photoKey() + (thumbnail ? "-thumb" : ""));
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read profile photo", exception);
        }
    }

    private ProfileView toView(UUID viewer, Profile profile) {
        UUID owner = profile.userId();
        boolean self = viewer.equals(owner);
        boolean photo = profile.photoKey() != null && (self || canSeePhoto(viewer, owner));
        String about = self || policies.stream().allMatch(policy -> policy.canSeeAbout(viewer, owner))
                ? profile.about() : null;
        return new ProfileView(owner, profile.displayName(), about, photo, profile.updatedAt());
    }

    private boolean canSeePhoto(UUID viewer, UUID owner) {
        return viewer.equals(owner) || policies.stream().allMatch(policy -> policy.canSeePhoto(viewer, owner));
    }

    private Profile load(UUID userId) {
        return repository.findById(userId).orElseThrow(() -> new IllegalStateException("Profile missing"));
    }

    private void deleteQuietly(@Nullable String key) {
        if (key == null) {
            return;
        }
        try {
            storage.delete(key);
            storage.delete(key + "-thumb");
        } catch (IOException exception) {
            // Arquivo órfão não quebra a troca de foto; o job de limpeza de mídia (Fase 3) recolhe.
        }
    }

    static String normalizeDisplayName(String raw) {
        String value = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFC).strip()
                .replaceAll("\\p{Cntrl}", "");
        if (value.isEmpty() || value.length() > Profile.DISPLAY_NAME_MAX) {
            throw new InvalidDisplayNameException();
        }
        return value;
    }

    /** Nome de exibição vazio ou longo demais. */
    static class InvalidDisplayNameException extends ApiException {
        InvalidDisplayNameException() {
            super(HttpStatus.BAD_REQUEST, "display-name-invalid", "Invalid display name");
        }
    }
}
