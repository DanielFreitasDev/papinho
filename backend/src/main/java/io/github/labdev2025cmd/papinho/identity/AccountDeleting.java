package io.github.labdev2025cmd.papinho.identity;

import java.util.UUID;

/**
 * A conta vai ser apagada (BK-02). Publicado de forma síncrona, na transação, antes de apagar a linha: os módulos
 * arrumam o que a cascata do banco não resolve (sair dos grupos passando o admin adiante, por exemplo).
 */
public record AccountDeleting(UUID userId) {
}
