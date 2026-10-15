package io.github.labdev2025cmd.papinho.messaging;

/** Tipo de conversa. */
public enum ConversationKind {
    /** Conversa individual entre duas pessoas. */
    DIRECT,
    /** Grupo (GR-01). */
    GROUP,
    /** "Mensagens salvas": conversa consigo mesmo (MS-12). */
    SELF,
    /** Lista de transmissão (GR-03): só o dono participa; cada envio vai para as conversas individuais. */
    BROADCAST
}
