package io.github.labdev2025cmd.papinho.media;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Quais arquivos outro módulo ainda usa (anexo de mensagem, figurinha, foto de grupo), para a limpeza dos que
 * foram enviados e nunca usados (MD-01). Implementada por quem guarda as referências, sem que a mídia dependa
 * desses módulos, como {@link MediaAccessPolicy}. É chamada dentro da transação da limpeza, com os arquivos já
 * travados.
 */
public interface MediaReferences {

    /** Entre os arquivos dados, os que o módulo ainda usa. */
    Set<UUID> inUse(Collection<UUID> mediaIds);
}
