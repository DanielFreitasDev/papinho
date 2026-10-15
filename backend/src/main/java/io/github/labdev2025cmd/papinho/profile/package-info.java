/**
 * Perfil público (ID-02): nome de exibição, foto e recado, separados do {@code @usuario}.
 * Quem pode ver foto e recado é decidido por {@link io.github.labdev2025cmd.papinho.profile.ProfileVisibilityPolicy},
 * implementada pelos módulos de contatos (bloqueio) e privacidade.
 */
@ApplicationModule(displayName = "Perfil")
@NullMarked
package io.github.labdev2025cmd.papinho.profile;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
