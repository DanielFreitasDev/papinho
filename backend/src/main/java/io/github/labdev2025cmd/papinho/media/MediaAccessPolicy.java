package io.github.labdev2025cmd.papinho.media;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Quem pode abrir um arquivo além de quem o enviou. Implementada pelo módulo de mensagens (o arquivo está numa
 * mensagem de uma conversa da pessoa), sem que a mídia dependa dele.
 */
public interface MediaAccessPolicy {

    boolean canRead(UUID viewer, UUID mediaId);

    /**
     * O arquivo só abre por um caminho próprio do módulo dono (visualização única, MS-08): nem quem enviou nem
     * os outros o leem pelas rotas normais de mídia.
     */
    default boolean restricted(UUID mediaId) {
        return false;
    }

    /**
     * Entre os arquivos de {@code owner}, os que ficam de fora da exportação da conta nesta requisição: anexos de
     * conversa trancada que o token não abre (PV-03).
     */
    default Set<UUID> hiddenFromOwner(UUID owner, Collection<UUID> mediaIds) {
        return Set.of();
    }
}
