package io.github.labdev2025cmd.papinho.messaging;

/** Mensagem de uma pessoa ou aviso da conversa ("Ana criou o grupo"). */
public enum MessageKind {
    USER,
    /** Aviso do sistema: o corpo é um JSON pequeno ({@link SystemNotice}) que o cliente traduz. */
    SYSTEM
}
