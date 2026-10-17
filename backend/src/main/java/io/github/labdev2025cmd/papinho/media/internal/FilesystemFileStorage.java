package io.github.labdev2025cmd.papinho.media.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import io.github.labdev2025cmd.papinho.media.FileStorage;

/**
 * Armazenamento em disco (padrão da auto-hospedagem). As chaves viram caminhos relativos ao diretório raiz;
 * só são aceitos caracteres seguros, então nenhuma chave escapa do diretório (path traversal).
 */
@Component
@ConditionalOnProperty(prefix = "app.storage", name = "type", havingValue = "filesystem", matchIfMissing = true)
class FilesystemFileStorage implements FileStorage {

    static final Pattern SAFE_KEY = Pattern.compile("^[a-z0-9][a-z0-9/_-]{0,200}$");

    private final Path root;

    FilesystemFileStorage(StorageProperties properties) throws IOException {
        this.root = properties.dir().toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public long write(String key, InputStream content) throws IOException {
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling("." + UUID.randomUUID() + ".tmp");
        try {
            long size = Files.copy(content, temporary);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return size;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @Override
    public Optional<InputStream> read(String key) throws IOException {
        Path path = resolve(key);
        return Files.isRegularFile(path) ? Optional.of(Files.newInputStream(path)) : Optional.empty();
    }

    @Override
    public Optional<Resource> resource(String key) {
        Path path = resolve(key);
        return Files.isRegularFile(path) ? Optional.of(new FileSystemResource(path)) : Optional.empty();
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    @Override
    public Optional<Space> space() {
        try {
            java.nio.file.FileStore store = Files.getFileStore(root);
            return Optional.of(new Space(store.getUsableSpace(), store.getTotalSpace()));
        } catch (IOException unavailable) {
            return Optional.empty();
        }
    }

    private Path resolve(String key) {
        if (!SAFE_KEY.matcher(key).matches() || key.contains("//")) {
            throw new IllegalArgumentException("Unsafe storage key");
        }
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Unsafe storage key");
        }
        return path;
    }

}
