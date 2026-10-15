---
name: Aviso de mensagem durante uma sincronização se perdia
description: syncConversation reaproveitava a busca em andamento; a mudança avisada depois do início não vinha
type: project
category: bug
date: 2026-10-15
---
`syncConversation` agrupava chamadas simultâneas devolvendo a promessa em andamento. Quando o aviso da segunda
mensagem chegava enquanto a busca `afterSeq` da primeira ainda rodava, a busca já tinha lido o banco antes da
mudança: a segunda mensagem só aparecia no próximo aviso. Sintoma nos e2e: "2 mensagens não lidas", a segunda
foto ou o segundo link ausentes por 5 s, de forma intermitente.

Correção: um aviso durante a sincronização marca `syncAgain`; ao terminar, faz mais uma rodada.

**Evidência:** falhas intermitentes em `chat.spec.ts`, `media.spec.ts` e `links.spec.ts`; duas rodadas completas
verdes (29/29) depois da correção, 2026-10-15.

**Aplicabilidade:** qualquer deduplicação de requisições disparadas por eventos: deduplicar, sim; descartar o
evento, não.
