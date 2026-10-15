package io.github.labdev2025cmd.papinho.profile;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Perfil como visto por um usuário: foto e recado já filtrados pela política de visibilidade.
 *
 * @param userId id do dono
 * @param displayName nome de exibição
 * @param about recado, ou nulo se vazio ou oculto
 * @param hasPhoto há foto visível
 * @param updatedAt última alteração (versão da foto para cache)
 */
public record ProfileView(UUID userId, String displayName, @Nullable String about, boolean hasPhoto,
        Instant updatedAt) {
}
