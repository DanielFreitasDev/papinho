/**
 * Mídia e arquivos (MD-01, MD-02, MD-06): upload retomável por tus (ADR 0009) com limite de 1 GB e cota, tipo
 * detectado pelo conteúdo (Tika), imagens sem metadados com miniatura e ThumbHash, antivírus opcional e a porta
 * de armazenamento {@link io.github.labdev2025cmd.papinho.media.FileStorage} (ADR 0006). Quem pode abrir um
 * arquivo é decidido por {@link io.github.labdev2025cmd.papinho.media.MediaAccessPolicy}; quem ainda usa um
 * arquivo, por {@link io.github.labdev2025cmd.papinho.media.MediaReferences} (os nunca usados saem do servidor).
 */
@ApplicationModule(displayName = "Mídia")
@NullMarked
package io.github.labdev2025cmd.papinho.media;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
