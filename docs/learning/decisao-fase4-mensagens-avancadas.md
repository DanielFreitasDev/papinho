---
name: Decisões da Fase 4 que não estão óbvias no código
description: grupos, transmissão, temporárias, visualização única, figurinhas e GIFs — escolhas e limites
type: project
category: decision
date: 2026-10-15
---
- **Quem sai do grupo perde o histórico**: o estado `LEFT` fica fora do `requireMember` (404).
  Diferente do WhatsApp, que deixa ver o que já foi recebido. Revisitar se alguém pedir.
- **Avisos do sistema** são mensagens `kind = SYSTEM` com JSON no corpo e os nomes da época; não contam como não
  lidas e não se editam, fixam ou encaminham. "Sair silenciosamente" não gera aviso.
- **Leitura no grupo** = mínimo dos ponteiros dos outros membros ativos (`peerReadSeq`/`peerDeliveredSeq`).
- **Lista de transmissão** é uma conversa `BROADCAST` só do dono; cada envio copia a mensagem para a conversa
  individual de quem tem o dono nos contatos. Editar ou apagar depois só afeta a cópia do dono.
- **Mensagens temporárias** são apagadas de verdade pelo job (linha e arquivo); o cliente esconde as vencidas
  antes, pelo `expiresAt`. Avisos do sistema não expiram.
- **Visualização única**: token de 5 min emitido uma vez por destinatário (Range de vídeo), ADR 0012.
- **Figurinhas**: exclusão lógica (`removed_at`), porque o CHECK do corpo exige figurinha numa mensagem vazia.
- **Agendadas** saem pelo `MessageService.send` numa transação própria (`REQUIRES_NEW`); se falhar, ficam com
  `failed_at` para quem agendou.
- **GIFs e emojis** sem CDN (ADR 0011); `frimousse` recusado por ser 0.x.

**Evidência:** `GroupIT`, `BroadcastIT`, `DisappearingIT`, `ViewOnceIT`, `StickerIT`, `ScheduledIT`, `GifIT`
verdes na passada 3 (2026-10-15).
