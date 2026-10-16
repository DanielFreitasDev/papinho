---
name: Aviso de mensagem durante uma sincronização se perdia
description: syncConversation reaproveitava a busca em andamento ou ignorava a primeira busca da conversa; a mudança avisada não vinha
type: project
category: bug
date: 2026-10-15
---
`syncConversation` agrupava chamadas simultâneas devolvendo a promessa em andamento. Quando o aviso da segunda
mensagem chegava enquanto a busca `afterSeq` da primeira ainda rodava, a busca já tinha lido o banco antes da
mudança: a segunda mensagem só aparecia no próximo aviso. Sintoma nos e2e: "2 mensagens não lidas", a segunda
foto ou o segundo link ausentes por 5 s, de forma intermitente.

Correção: um aviso durante a sincronização marca `syncAgain`; ao terminar, faz mais uma rodada.

Segundo caso (2026-10-16): sem cache da conversa, `syncConversation` simplesmente saía. Se o aviso (ou a
sincronização ao conectar o WebSocket) chegava enquanto a **primeira** busca da conversa ainda rodava, essa busca
tinha saído antes da mensagem e a deixava de fora; a lista mostrava "1 mensagem não lida" e a conversa,
"Nenhuma mensagem ainda". `invalidateQueries`/`refetchQueries` não resolvem: o TanStack Query não cancela uma
busca em andamento de consulta ainda sem dados, só se junta a ela. Correção: esperar a busca terminar e continuar
do cursor que ela trouxer (`sync.test.ts`).

**Evidência:** falhas intermitentes em `chat.spec.ts`, `media.spec.ts` e `links.spec.ts`; duas rodadas completas
verdes (29/29) depois da correção, 2026-10-15. Segundo caso: `privacy.spec.ts` falhou nos dois projetos na suíte
completa; `sync.test.ts` falha sem a correção e passa com ela; `privacy`, `chat` e `pwa` 13/13 depois, 2026-10-16.

**Aplicabilidade:** qualquer deduplicação de requisições disparadas por eventos: deduplicar, sim; descartar o
evento, não.
