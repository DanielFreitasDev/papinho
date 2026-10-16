package io.github.labdev2025cmd.papinho.messaging;

import java.util.UUID;

/**
 * Mensagem nova de uma pessoa (texto, mídia, figurinha ou enquete; nunca aviso do sistema). Publicado na
 * transação do envio e entregue pelo registro de eventos do Spring Modulith (ADR 0005). Leva só ids: o conteúdo
 * é lido na hora de notificar, para nada da mensagem ficar guardado no registro de eventos.
 */
public record MessagePosted(UUID messageId, UUID conversationId) {
}
