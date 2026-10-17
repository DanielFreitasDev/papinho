---
name: Sync por afterSeq perdia mensagens com o lastSeq lido depois da busca
description: em READ COMMITTED cada comando vê um retrato novo; leia o seq atual antes e limite a busca a ele
type: project
category: bug
date: 2026-10-17
---
`sync` e `history` buscavam as mensagens e só depois liam `conversations.last_seq`, em comandos separados da
mesma transação `readOnly` (READ COMMITTED). Uma mensagem confirmada entre os dois ficava fora da página mas
dentro do `lastSeq`; o cliente (`cache.ts`) avançava o cursor para o `lastSeq` e a pulava para sempre (11 de 300
na auditoria de 2026-10-17, achado A03).

**Regra:** leia o `lastSeq` primeiro e busque `change_seq <= lastSeq`. Como as escritas na conversa ficam em
fila pela trava da linha (`SequenceAllocator`), tudo até esse seq já está confirmado quando a busca roda.

**Como provar corrida sem sorte:** `@MockitoSpyBean` no `SequenceAllocator` e um `doAnswer` que, na thread do
teste, confirma um envio noutra thread antes de chamar o método real (`SyncRaceIT`). Determinístico; com a ordem
antiga falha sempre.

**Evidência:** `SyncRaceIT` falha nos dois testes com a leitura depois da busca e passa com a correção
(commit `235ac72`).
