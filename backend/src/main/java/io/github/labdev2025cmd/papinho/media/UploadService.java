package io.github.labdev2025cmd.papinho.media;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.tika.Tika;
import org.apache.tika.io.TikaInputStream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.labdev2025cmd.papinho.admin.InstanceSettingsService;
import io.github.labdev2025cmd.papinho.shared.security.RateLimiter;

/**
 * Upload retomável pelo protocolo tus 1.0 (MD-01, ADR 0009): criação com tamanho declarado (limite de 1 GB e
 * cota por usuário), escrita em pedaços com {@code Upload-Offset}, consulta do deslocamento para retomar e
 * cancelamento. Ao completar, o tipo é detectado pelo conteúdo (Tika), imagens são tratadas (MD-02), o
 * antivírus opcional analisa (MD-06) e o arquivo vai para a porta de armazenamento.
 *
 * <p>Os pedaços ficam num diretório local até completar; um pedaço por vez por upload (trava em memória:
 * instância única, ADR 0004).</p>
 */
@Service
public class UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadService.class);
    /** Imagens maiores que isso viram documento: o tratamento é feito em memória. */
    private static final long MAX_PROCESSED_IMAGE = 50L * 1024 * 1024;
    /** Espaço do advisory lock que serializa a conta da cota de cada pessoa ("cota"). */
    private static final int QUOTA_LOCK = 0x636F7461;

    private final MediaUploadRepository uploads;
    private final MediaFileRepository files;
    private final FileStorage storage;
    private final MalwareScanner scanner;
    private final InstanceSettingsService settings;
    private final RateLimiter rateLimiter;
    private final TransactionTemplate transactions;
    private final JdbcTemplate jdbc;
    private final MediaProperties properties;
    private final Clock clock;
    private final Path stagingDir;
    private final Tika tika = new Tika();
    private final Map<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();

    UploadService(MediaUploadRepository uploads, MediaFileRepository files, FileStorage storage,
            MalwareScanner scanner, InstanceSettingsService settings, RateLimiter rateLimiter,
            TransactionTemplate transactions, JdbcTemplate jdbc, MediaProperties properties, Clock clock)
            throws IOException {
        this.uploads = uploads;
        this.files = files;
        this.storage = storage;
        this.scanner = scanner;
        this.settings = settings;
        this.rateLimiter = rateLimiter;
        this.transactions = transactions;
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
        this.stagingDir = properties.uploadDir().toAbsolutePath().normalize();
        Files.createDirectories(stagingDir);
    }

    /** Cria um upload; recusa acima de 1 GB ou além da cota restante do usuário. */
    public UploadStatus create(UUID owner, long length, @Nullable String filename, @Nullable String declaredType) {
        rateLimiter.consume(RateLimiter.Policy.UPLOAD_BY_USER, owner.toString());
        if (length < 1) {
            throw new MediaExceptions.InvalidUploadException("empty");
        }
        if (length > MediaProperties.MAX_FILE_SIZE) {
            throw new MediaExceptions.TooLargeException();
        }
        long quota = settings.current().userQuotaBytes();
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        MediaUpload upload = transactions.execute(status -> {
            // Dois uploads da mesma pessoa ao mesmo tempo leriam o mesmo uso e passariam os dois da cota.
            jdbc.queryForObject("select pg_advisory_xact_lock(?, ?)", Object.class, QUOTA_LOCK, owner.hashCode());
            long used = files.usedBy(owner) + uploads.reservedBy(owner);
            if (used + length > quota) {
                throw new MediaExceptions.QuotaExceededException(Math.max(0, quota - used));
            }
            return uploads.save(new MediaUpload(id, owner, length,
                    FileNames.sanitize(filename == null ? "" : filename), clean(declaredType), now,
                    now.plus(properties.uploadExpiry())));
        });
        try {
            Files.createFile(staged(id));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create staging file", exception);
        }
        return new UploadStatus(upload.id(), 0, upload.length(), upload.expiresAt(), null);
    }

    /**
     * Guarda um arquivo que o servidor buscou em nome do usuário (GIF do provedor, MS-10), pelo mesmo caminho do
     * upload: limite, cota, tipo pelo conteúdo, antivírus e limpeza de metadados.
     */
    public MediaView ingest(UUID owner, String filename, @Nullable String declaredType, byte[] content)
            throws IOException {
        UploadStatus status = create(owner, content.length, filename, declaredType);
        MediaUpload upload = require(owner, status.id());
        Path file = staged(status.id());
        Files.write(file, content);
        return complete(upload, file);
    }

    /** Deslocamento atual, para o cliente retomar ({@code HEAD}). */
    public UploadStatus status(UUID owner, UUID id) throws IOException {
        MediaUpload upload = require(owner, id);
        return new UploadStatus(id, Files.size(staged(id)), upload.length(), upload.expiresAt(), null);
    }

    /**
     * Grava um pedaço a partir de {@code offset}. Se o pedaço completar o arquivo, finaliza e devolve a mídia.
     *
     * @throws MediaExceptions.OffsetConflictException se {@code offset} não for o tamanho já recebido
     */
    public UploadStatus append(UUID owner, UUID id, long offset, InputStream body) throws IOException {
        MediaUpload upload = require(owner, id);
        ReentrantLock lock = locks.computeIfAbsent(id, ignored -> new ReentrantLock());
        if (!lock.tryLock()) {
            throw new MediaExceptions.UploadBusyException();
        }
        try {
            Path file = staged(id);
            long current = Files.size(file);
            if (offset != current) {
                throw new MediaExceptions.OffsetConflictException(current);
            }
            long remaining = upload.length() - current;
            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
                    OutputStream out = Channels.newOutputStream(channel)) {
                long copied = copy(body, out, remaining);
                channel.force(false);
                if (copied == remaining && body.read() != -1) {
                    channel.truncate(current);
                    throw new MediaExceptions.InvalidUploadException("more bytes than Upload-Length");
                }
            }
            long received = Files.size(file);
            if (received < upload.length()) {
                return new UploadStatus(id, received, upload.length(), upload.expiresAt(), null);
            }
            return new UploadStatus(id, received, upload.length(), upload.expiresAt(), complete(upload, file));
        } finally {
            lock.unlock();
            locks.remove(id, lock);
        }
    }

    /** Cancela (tus termination): apaga o parcial. */
    public void cancel(UUID owner, UUID id) throws IOException {
        require(owner, id);
        transactions.executeWithoutResult(status -> uploads.deleteById(id));
        Files.deleteIfExists(staged(id));
    }

    /** Uploads abandonados liberam a cota e o disco. */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    void expire() {
        for (MediaUpload upload : uploads.findByExpiresAtBefore(clock.instant())) {
            try {
                transactions.executeWithoutResult(status -> uploads.deleteById(upload.id()));
                Files.deleteIfExists(staged(upload.id()));
            } catch (IOException | RuntimeException exception) {
                log.warn("Could not remove expired upload {}", upload.id(), exception);
            }
        }
    }

    private MediaView complete(MediaUpload upload, Path file) throws IOException {
        String detected;
        try (InputStream in = TikaInputStream.get(file)) {
            detected = tika.detect(in, upload.filename());
        }
        String type = ContentTypes.resolve(detected, upload.declaredType());
        MediaKind kind = ContentTypes.kind(type);
        if (kind == MediaKind.IMAGE && Files.size(file) > MAX_PROCESSED_IMAGE) {
            kind = MediaKind.DOCUMENT;
        }
        if (!scanner.isClean(file)) {
            discard(upload.id(), file);
            throw new MediaExceptions.MalwareException();
        }
        UUID id = upload.id();
        String filename = upload.filename();
        Integer width = null;
        Integer height = null;
        String thumbhash = null;
        boolean thumbnail = false;
        String sha256;
        long size;
        try {
            if (kind == MediaKind.IMAGE) {
                PhotoPipeline.Result result = PhotoPipeline.process(Files.readAllBytes(file), type);
                size = storage.write(MediaFile.contentKey(id), new ByteArrayInputStream(result.content()));
                sha256 = HexFormat.of().formatHex(digest().digest(result.content()));
                width = result.width();
                height = result.height();
                thumbhash = result.thumbhash();
                if (result.thumbnail() != null) {
                    storage.write(MediaFile.thumbnailKey(id), new ByteArrayInputStream(result.thumbnail()));
                    thumbnail = true;
                }
            } else {
                MessageDigest digest = digest();
                try (InputStream in = new DigestInputStream(Files.newInputStream(file), digest)) {
                    size = storage.write(MediaFile.contentKey(id), in);
                }
                sha256 = HexFormat.of().formatHex(digest.digest());
            }
        } catch (ImageProcessing.InvalidImageException invalid) {
            discard(id, file);
            throw invalid;
        }
        MediaFile media = new MediaFile(id, upload.ownerId(), kind, type, size, filename, sha256, width, height,
                thumbhash, thumbnail, clock.instant());
        MediaFile saved = transactions.execute(status -> {
            uploads.deleteById(id);
            return files.save(media);
        });
        Files.deleteIfExists(file);
        return saved.toView();
    }

    private void discard(UUID id, Path file) throws IOException {
        transactions.executeWithoutResult(status -> uploads.deleteById(id));
        Files.deleteIfExists(file);
    }

    private MediaUpload require(UUID owner, UUID id) {
        return uploads.findById(id).filter(u -> u.ownerId().equals(owner))
                .orElseThrow(MediaExceptions.NotFoundException::new);
    }

    private Path staged(UUID id) {
        return stagingDir.resolve(id.toString());
    }

    private static long copy(InputStream in, OutputStream out, long limit) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        while (total < limit) {
            int read = in.read(buffer, 0, (int) Math.min(buffer.length, limit - total));
            if (read < 0) {
                break;
            }
            out.write(buffer, 0, read);
            total += read;
        }
        return total;
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static @Nullable String clean(@Nullable String declaredType) {
        if (declaredType == null || declaredType.isBlank() || declaredType.length() > 127) {
            return null;
        }
        return declaredType.strip();
    }

    /**
     * Estado de um upload.
     *
     * @param media a mídia criada, quando este pedaço completou o arquivo
     */
    public record UploadStatus(UUID id, long offset, long length, Instant expiresAt, @Nullable MediaView media) {
    }
}
