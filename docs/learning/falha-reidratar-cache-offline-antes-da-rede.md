---
name: Reidratar o cache do TanStack Query com o IndexedDB antes da rede
description: abordagem descartada no NT-05; o snapshot velho vencia o estado real e sumia com o marcador de não lidas
type: project
category: failed-approach
date: 2026-10-16
---
Na primeira versão do offline, ao abrir o app o cache era preenchido com o snapshot do IndexedDB
(`setQueryData(..., { updatedAt: 0 })`) e a rede atualizava depois. Quebrou o e2e `chat.spec.ts` (MS-14): a
conversa abria com os dados antigos (`unreadCount` 0), a tela guarda as não lidas do momento em que abre e o
marcador "2 mensagens não lidas" nunca aparecia. As mensagens têm `staleTime: Infinity` e também ficavam no
snapshot até a sincronização por `afterSeq`.

Ficou: rede primeiro, IndexedDB só quando o `fetch` falha sem resposta (`networkFirst` em `lib/offline-db.ts`),
com `networkMode: "always"` para a consulta rodar mesmo com o navegador offline.

**Evidência:** suíte e2e completa da passada 5 (chat.spec.ts nos dois projetos), 2026-10-16.
