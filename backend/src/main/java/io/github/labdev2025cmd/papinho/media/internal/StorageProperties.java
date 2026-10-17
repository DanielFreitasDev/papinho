package io.github.labdev2025cmd.papinho.media.internal;

import java.net.URI;
import java.nio.file.Path;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Onde ficam os arquivos enviados (prefixo {@code app.storage}, ADR 0006).
 *
 * @param type {@code filesystem} (padrão da auto-hospedagem) ou {@code s3} (produção)
 * @param dir raiz do armazenamento em disco, fora do repositório
 * @param s3 bucket S3-compatível (AWS S3, MinIO, Cloudflare R2...), usado com {@code type=s3}
 */
@ConfigurationProperties(prefix = "app.storage")
record StorageProperties(@DefaultValue("filesystem") String type, Path dir, @DefaultValue S3 s3) {

    /**
     * @param endpoint endereço do serviço; vazio usa o da AWS na região
     * @param region região do bucket
     * @param bucket bucket já criado, só para o Papinho
     * @param accessKey chave de acesso; vazia usa as credenciais do ambiente (variáveis AWS_*, perfil, papel)
     * @param secretKey segredo da chave de acesso
     * @param pathStyle endereço no caminho ({@code endpoint/bucket/chave}), como MinIO e a maioria dos
     *     compatíveis pedem
     */
    record S3(@Nullable URI endpoint, @DefaultValue("us-east-1") String region, @Nullable String bucket,
            @Nullable String accessKey, @Nullable String secretKey, @DefaultValue("true") boolean pathStyle) {
    }
}
