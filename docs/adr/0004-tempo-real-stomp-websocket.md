---
status: aceita
date: 2026-10-15
---

# 0004 — Tempo real com STOMP sobre WebSocket e broker simples

## Contexto e problema

Mensagens, confirmações de entrega e leitura, "digitando…" e presença precisam chegar em tempo real a
vários dispositivos de cada usuário.

## Decisão

- **Spring WebSocket** com **STOMP** no servidor e **`@stomp/stompjs`** no SPA, endpoint `/ws` na mesma
  origem.
- **Broker simples em memória** do Spring, com destinos por usuário (`/user/queue/...`): o servidor envia
  a cada sessão só o que pertence ao usuário autenticado.
- A fonte de verdade continua sendo o banco: o WebSocket só avisa; o cliente recupera o que perdeu com
  `afterSeq` (ADR 0010).
- **Caminho de escala documentado:** trocar o broker simples por *relay* para RabbitMQ
  (`enableStompBrokerRelay`) sem mudar o protocolo nem o cliente, quando houver mais de uma instância.

## Consequências

- Boas: protocolo maduro, reconexão e heartbeats prontos no cliente, autorização por destino no servidor.
- Ruins: broker em memória limita a uma instância; mensagens em trânsito se perdem num reinício (por
  isso a sincronização por `seq`).

## Alternativas consideradas

- WebSocket cru: teríamos de reinventar roteamento, assinaturas e heartbeats.
- Server-Sent Events: só servidor → cliente; "digitando" e confirmações precisariam de outro canal.
