package io.github.labdev2025cmd.papinho.media;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuração de uploads e do antivírus (prefixo {@code app.media}).
 *
 * @param uploadDir onde ficam os uploads tus em andamento (disco local, mesmo com S3)
 * @param uploadExpiry quanto tempo um upload incompleto espera ser retomado
 * @param unusedFileExpiry quanto tempo um arquivo enviado espera virar anexo, figurinha ou foto de grupo antes de
 *     sair do servidor (MD-01)
 * @param clamav antivírus opcional (MD-06)
 */
@ConfigurationProperties(prefix = "app.media")
public record MediaProperties(Path uploadDir, @DefaultValue("24h") Duration uploadExpiry,
        @DefaultValue("24h") Duration unusedFileExpiry, @DefaultValue ClamAv clamav) {

    /** Tamanho máximo de um arquivo (MD-01). */
    public static final long MAX_FILE_SIZE = 1L << 30;

    /**
     * @param enabled liga a análise de cada arquivo recebido
     * @param host endereço do clamd
     * @param port porta TCP do clamd
     * @param timeout tempo máximo de conexão e de resposta
     */
    public record ClamAv(@DefaultValue("false") boolean enabled, @DefaultValue("localhost") String host,
            @DefaultValue("3310") int port, @DefaultValue("60s") Duration timeout) {
    }
}
