package io.github.labdev2025cmd.papinho.media.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import io.github.labdev2025cmd.papinho.media.FileStorage;
import jakarta.annotation.PreDestroy;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Armazenamento S3-compatível para produção (ADR 0006): AWS S3, MinIO, Cloudflare R2 e afins. As chaves são as
 * mesmas do disco (opacas, sem o nome do usuário). O conteúdo passa por um arquivo temporário antes do envio,
 * porque o S3 pede o tamanho de antemão; leituras com {@code Range} pedem só a faixa ao bucket.
 */
@Component
@ConditionalOnProperty(prefix = "app.storage", name = "type", havingValue = "s3")
class S3FileStorage implements FileStorage {

    private final S3Client client;
    private final String bucket;

    S3FileStorage(StorageProperties properties) {
        StorageProperties.S3 s3 = properties.s3();
        if (s3.bucket() == null || s3.bucket().isBlank()) {
            throw new IllegalStateException("app.storage.s3.bucket is required with app.storage.type=s3");
        }
        this.bucket = s3.bucket();
        S3ClientBuilder builder = S3Client.builder().region(Region.of(s3.region())).forcePathStyle(s3.pathStyle());
        if (s3.endpoint() != null && !s3.endpoint().toString().isBlank()) {
            builder.endpointOverride(s3.endpoint());
        }
        builder.credentialsProvider(s3.accessKey() == null || s3.accessKey().isBlank()
                ? DefaultCredentialsProvider.builder().build()
                : StaticCredentialsProvider.create(AwsBasicCredentials.create(s3.accessKey(), s3.secretKey())));
        this.client = builder.build();
    }

    @PreDestroy
    void close() {
        client.close();
    }

    @Override
    public long write(String key, InputStream content) throws IOException {
        String safe = safe(key);
        Path temporary = Files.createTempFile("papinho-s3-", ".tmp");
        try {
            long size = Files.copy(content, temporary, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            client.putObject(request -> request.bucket(bucket).key(safe).contentLength(size),
                    RequestBody.fromFile(temporary));
            return size;
        } catch (SdkException failed) {
            throw new IOException("S3 write failed", failed);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @Override
    public Optional<InputStream> read(String key) throws IOException {
        String safe = safe(key);
        try {
            return Optional.of(client.getObject(request -> request.bucket(bucket).key(safe)));
        } catch (NoSuchKeyException missing) {
            return Optional.empty();
        } catch (SdkException failed) {
            throw new IOException("S3 read failed", failed);
        }
    }

    @Override
    public Optional<Resource> resource(String key) throws IOException {
        String safe = safe(key);
        try {
            HeadObjectResponse head = client.headObject(request -> request.bucket(bucket).key(safe));
            return Optional.of(new ObjectResource(safe, head.contentLength()));
        } catch (S3Exception failed) {
            if (failed.statusCode() == 404) {
                return Optional.empty();
            }
            throw new IOException("S3 head failed", failed);
        } catch (SdkException failed) {
            throw new IOException("S3 head failed", failed);
        }
    }

    @Override
    public void delete(String key) throws IOException {
        String safe = safe(key);
        try {
            client.deleteObject(request -> request.bucket(bucket).key(safe));
        } catch (SdkException failed) {
            throw new IOException("S3 delete failed", failed);
        }
    }

    private static String safe(String key) {
        if (!FilesystemFileStorage.SAFE_KEY.matcher(key).matches() || key.contains("//")) {
            throw new IllegalArgumentException("Unsafe storage key");
        }
        return key;
    }

    /**
     * Abre o objeto só na primeira leitura: um {@code skip} antes disso (é como o Spring responde a um
     * {@code Range}) vira o começo da faixa pedida ao bucket, sem baixar o que vem antes.
     */
    private final class RangedObjectStream extends InputStream {

        private final String key;
        private final long length;
        private long start;
        private @Nullable InputStream delegate;

        RangedObjectStream(String key, long length) {
            this.key = key;
            this.length = length;
        }

        @Override
        public long skip(long count) throws IOException {
            if (delegate == null) {
                long skipped = Math.max(0, Math.min(count, length - start));
                start += skipped;
                return skipped;
            }
            return delegate.skip(count);
        }

        @Override
        public int read() throws IOException {
            return open().read();
        }

        @Override
        public int read(byte[] buffer, int offset, int count) throws IOException {
            return open().read(buffer, offset, count);
        }

        @Override
        public void close() throws IOException {
            if (delegate != null) {
                delegate.close();
            }
        }

        private InputStream open() throws IOException {
            if (delegate == null) {
                if (start >= length) {
                    delegate = InputStream.nullInputStream();
                } else {
                    try {
                        delegate = client.getObject(request -> request.bucket(bucket).key(key)
                                .range(start == 0 ? null : "bytes=" + start + "-"));
                    } catch (SdkException failed) {
                        throw new IOException("S3 read failed", failed);
                    }
                }
            }
            return delegate;
        }
    }

    /** Objeto do bucket como {@link Resource}, com o tamanho já conhecido para responder com faixas. */
    private final class ObjectResource extends AbstractResource {

        private final String key;
        private final long length;

        ObjectResource(String key, long length) {
            this.key = key;
            this.length = length;
        }

        @Override
        public InputStream getInputStream() {
            return new RangedObjectStream(key, length);
        }

        @Override
        public long contentLength() {
            return length;
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public String getDescription() {
            return "S3 object [" + key + "]";
        }
    }
}
