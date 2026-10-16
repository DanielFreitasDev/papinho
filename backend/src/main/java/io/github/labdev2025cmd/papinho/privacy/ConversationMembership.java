package io.github.labdev2025cmd.papinho.privacy;

import java.util.UUID;

/**
 * Porta para saber se alguém participa de uma conversa, implementada pelo módulo de mensagens (que consulta este
 * módulo e por isso não pode ser chamado diretamente daqui).
 */
public interface ConversationMembership {

    /** {@code userId} participa (ativo ou solicitação) da conversa? */
    boolean isMember(UUID userId, UUID conversationId);
}
