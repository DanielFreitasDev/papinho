---
status: aceita
date: 2026-10-15
---

# 0010 — Sincronização por número de sequência por conversa e `clientMessageId` idempotente

## Contexto e problema

Clientes caem, reconectam, trocam de rede e enviam offline (NT-05). Precisamos que nada se perca e nada
se duplique, em vários dispositivos.

## Decisão

- Cada mensagem recebe um **`seq` monotônico por conversa**, atribuído pelo servidor na mesma transação
  da gravação (contador da conversa com lock de linha).
- Ao conectar ou reconectar, o cliente pede **`afterSeq`** por conversa e recebe exatamente o que perdeu,
  em ordem; o WebSocket (ADR 0004) só avisa que há novidade.
- O cliente gera um **`clientMessageId`** (UUID) por mensagem; o servidor tem restrição única
  `(sender_id, client_message_id)`: um reenvio devolve a mensagem já gravada em vez de duplicar.
- Edições, apagamentos e reações também avançam o `seq` da conversa (eventos de alteração), para o
  cliente convergir sem recarregar tudo.

## Consequências

- Boas: reconexão barata, fila offline segura, testes determinísticos (RT-01, RT-02).
- Ruins: o contador por conversa serializa escritas na mesma conversa (aceitável para chat).

## Alternativas consideradas

- Ordenar por timestamp: relógios divergem e empates quebram a paginação.
- IDs globais com lacunas (sequence do PostgreSQL): o cliente não distingue lacuna de perda.
