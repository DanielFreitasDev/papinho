/**
 * Código transversal realmente compartilhado: segurança base, erros em Problem Details, SPA e relógio.
 * Módulo aberto: os demais módulos podem usar qualquer tipo daqui; ele não depende de nenhum módulo de negócio.
 */
@ApplicationModule(displayName = "Shared", type = ApplicationModule.Type.OPEN)
@NullMarked
package io.github.labdev2025cmd.papinho.shared;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
