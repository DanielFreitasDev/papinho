/**
 * Administração da instância (AD-01, AD-02): configurações persistidas no banco com padrões seguros.
 * Cada módulo expõe os próprios endpoints administrativos (usuários em identity, denúncias em moderation);
 * este módulo não depende de nenhum módulo de negócio, para que todos possam ler as configurações.
 */
@ApplicationModule(displayName = "Administração")
@NullMarked
package io.github.labdev2025cmd.papinho.admin;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
