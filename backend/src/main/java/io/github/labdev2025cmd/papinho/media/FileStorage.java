package io.github.labdev2025cmd.papinho.media;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import org.springframework.core.io.Resource;

/**
 * Porta de armazenamento de arquivos (ADR 0006). Chaves são opacas e geradas pelo chamador (nunca o nome
 * enviado pelo usuário); implementações: sistema de arquivos (padrão) e S3-compatível (produção).
 */
public interface FileStorage {

    /** Grava o conteúdo sob a chave, substituindo o anterior; devolve o tamanho gravado. */
    long write(String key, InputStream content) throws IOException;

    /** Abre o conteúdo para leitura, se existir. O chamador fecha o stream. */
    Optional<InputStream> read(String key) throws IOException;

    /**
     * O arquivo como {@link Resource} com tamanho conhecido, para respostas com faixa de bytes ({@code Range})
     * de vídeo e áudio.
     */
    Optional<Resource> resource(String key) throws IOException;

    /** Apaga o arquivo; não falha se ele não existir. */
    void delete(String key) throws IOException;

    /** Espaço do volume onde os arquivos ficam (AD-01); vazio quando o armazenamento não informa (S3). */
    default Optional<Space> space() {
        return Optional.empty();
    }

    /** Bytes livres e totais do volume. */
    record Space(long usableBytes, long totalBytes) {
    }
}
