/**
 * Mídia e arquivos (MD-*). Por enquanto, a porta de armazenamento {@link io.github.labdev2025cmd.papinho.media.FileStorage}
 * (ADR 0006) com a implementação em sistema de arquivos; upload retomável, Tika e miniaturas chegam na Fase 3.
 */
@ApplicationModule(displayName = "Mídia")
@NullMarked
package io.github.labdev2025cmd.papinho.media;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
