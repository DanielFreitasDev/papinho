package io.github.labdev2025cmd.papinho.messaging;

/** Situação de um participante na conversa (CT-02). */
public enum MemberState {
    /** Conversa normal. */
    ACTIVE,
    /** Solicitação de mensagem de quem não está nos contatos, ainda não aceita. */
    REQUEST,
    /** Solicitação recusada: a conversa some da lista e não notifica. */
    DECLINED,
    /** Saiu do grupo ou foi removido: não vê mais a conversa (GR-01). */
    LEFT,
    /** Pediu para entrar no grupo pelo link e espera a aprovação de um admin (GR-02). */
    PENDING
}
