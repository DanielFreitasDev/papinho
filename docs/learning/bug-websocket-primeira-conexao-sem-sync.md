---
name: Aviso perdido antes da primeira conexão do WebSocket
description: a lista e a conversa aberta só ressincronizavam em reconexões, não na primeira conexão
type: project
category: bug
date: 2026-10-16
---
**Sintoma:** no e2e, a pessoa recarregava a conversa e a mensagem enviada logo depois não aparecia (lista vazia,
"Nenhuma mensagem ainda") até outro evento.

**Causa:** `useRealtimeSync` só invalidava a lista e chamava `syncOpen()` quando `reconnected` era verdadeiro.
Entre a busca inicial da tela e a assinatura de `/user/queue/events` há uma janela em que o aviso `messages` é
publicado e ninguém escuta.

**Solução:** sincronizar em **toda** conexão (`state === "connected"`), inclusive a primeira; o `afterSeq` não
duplica nada (RT-01).

**Evidência:** `privacy.spec.ts` falhou no desktop antes e passou depois, 2026-10-16.
