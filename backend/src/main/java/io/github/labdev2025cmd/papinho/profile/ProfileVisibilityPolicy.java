package io.github.labdev2025cmd.papinho.profile;

import java.util.UUID;

/**
 * Porta que decide o que um usuário pode ver do perfil de outro. Implementada por outros módulos
 * (bloqueio em contatos, PV-04 em privacidade); todas as implementações precisam permitir.
 */
public interface ProfileVisibilityPolicy {

    /** {@code viewer} pode ver a foto de {@code owner}? */
    boolean canSeePhoto(UUID viewer, UUID owner);

    /** {@code viewer} pode ver o recado de {@code owner}? */
    boolean canSeeAbout(UUID viewer, UUID owner);
}
