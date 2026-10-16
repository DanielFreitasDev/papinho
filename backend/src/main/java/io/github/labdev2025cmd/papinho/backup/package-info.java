/**
 * Backup e dados (BK-02): exportação dos dados de uma pessoa para a portabilidade da LGPD, juntando as seções que
 * cada módulo descreve ({@link io.github.labdev2025cmd.papinho.shared.personal.PersonalDataSection}). O backup da
 * instância inteira (BK-03) é feito por script, fora da aplicação.
 */
@ApplicationModule(displayName = "Backup")
@NullMarked
package io.github.labdev2025cmd.papinho.backup;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
