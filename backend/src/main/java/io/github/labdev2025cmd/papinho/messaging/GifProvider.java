package io.github.labdev2025cmd.papinho.messaging;

import java.net.URI;
import java.util.List;

/** Porta dos provedores de GIF (MS-10); a implementação padrão está desligada. */
interface GifProvider {

    boolean enabled();

    /** Busca (ou os em alta, com {@code query} vazia). */
    List<Gif> search(String query, String locale, int limit);

    /**
     * GIF do provedor.
     *
     * @param preview versão pequena para a lista
     * @param original versão para enviar
     */
    record Gif(String id, String title, URI preview, URI original, int width, int height) {
    }
}
