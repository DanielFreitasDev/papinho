/**
 * Conversas e mensagens (MS-01..04, MS-14, RT-01, RT-02, CT-02): conversa individual por par de pessoas,
 * {@code seq} monotônico por conversa, {@code clientMessageId} idempotente, confirmações de entrega e
 * leitura, reações, respostas, encaminhamento, fixadas, favoritas e solicitações de mensagem. O WebSocket
 * só avisa que há novidade; o cliente sincroniza por {@code afterSeq} (ADR 0004 e 0010).
 */
@ApplicationModule(displayName = "Mensagens")
@NullMarked
package io.github.labdev2025cmd.papinho.messaging;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
