---
name: Fase 6 — organização da lista, busca, notificações e offline
description: escolhas da Fase 6 que o código não explica sozinho (arquivo, apagar conversa, busca, push, offline)
type: project
category: decision
date: 2026-10-16
---
- **Arquivada é calculada, não gravada a cada mensagem:** `archived_seq` guarda o seq ao arquivar; a lista
  considera arquivada enquanto não houver mensagem de outra pessoa depois dele (ou sempre, com "manter
  arquivadas"). Nenhum caminho de envio precisa mexer no arquivo.
- **Apagar conversa** esconde todas as mensagens (como "apagar para mim") e grava `removed_seq`: a conversa volta
  sozinha quando chega mensagem com seq maior. Em grupo a pessoa continua membro (sair é outra ação).
- **Limpar** reaproveita `message_hidden`, então histórico, afterSeq, prévia e busca já respeitam sem código novo.
- **Fixar** usa trava consultiva por pessoa (`pins:<id>`) para o limite de 3 valer com dois aparelhos ao mesmo
  tempo; arquivar solta.
- **Busca:** `websearch_to_tsquery('papinho_portuguese')` (português sem acento, radicais) **ou**
  `papinho_unaccent(body) LIKE` com índice de trigramas para trechos. O padrão do LIKE passa pela mesma função
  dentro da SQL (minúsculas e sem acento dos dois lados). Conversas e contatos são filtrados no cliente.
- **Notificação decidida no servidor** e entregue por WebSocket e Web Push com a mesma `tag` (ADR 0014). O evento
  `MessagePosted` leva só ids: o registro de eventos do Modulith não guarda texto de mensagem.
- **Offline:** banco Dexie por conta, **rede primeiro**: as consultas de lista, conversa e mensagens rodam com
  `networkMode: "always"` e só usam o IndexedDB quando o `fetch` falha sem resposta do servidor. A fila grava no
  IndexedDB antes do POST, para não perder o texto se a rede cair no meio.

**Evidência:** `ChatListIT` (5), `SearchIT` (3), `NotificationIT` (6), `WebPushCryptoTest` (4); e2e
`organize.spec.ts` e `pwa.spec.ts` nos dois projetos, 2026-10-16.

**Aplicabilidade:** novas ações na lista e novos tipos de mensagem (avaliar notificação e busca).
