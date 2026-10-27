---
name: Linha da conversa recorta o que passa dela
description: content-visibility auto das linhas liga contenção de pintura; reação e rabinho precisam caber na linha, e o voo da mensagem sai dela por uma cópia fixa
type: project
category: bug
date: 2026-10-27
---
Tudo o que é desenhado fora da bolha (pílula de reações, rabinho, voo da mensagem enviada) precisa caber na caixa da
própria linha da conversa ou ser desenhado fora da lista: a linha não pinta nada além da borda dela.

**Problema/Contexto:** as reações pareciam "cortadas pela mensagem de baixo". A causa é o `MessageScrollerItem`
(shadcn), que tem `[content-visibility:auto]` (UI-04, desempenho com milhares de mensagens): `content-visibility:
auto` liga sempre contenção de layout, estilo e **pintura**, e a contenção de pintura recorta na borda da linha. A
pílula descia 21 px da bolha (`translate-y-3/4` + anel de 3 px) e a bolha reservava só 12 px (`mb-3`). Não era
z-index nem a linha de baixo por cima.
**Por que importa:** qualquer enfeite novo fora da bolha (rabinho, selo, sombra grande, animação que sai do lugar)
some sem erro nenhum; e mexer no `content-visibility` derruba o UI-04.
**Como aplicar:** reserve o espaço dentro da linha: a pílula entra 6 px na bolha e a bolha tem `mb-6`; o rabinho (8
px) cabe porque a linha tem `px-2.5`. O que precisa atravessar a lista (a mensagem que voa do campo até o lugar
dela) é uma cópia da bolha presa à janela (`position: fixed` no `body`, `features/chat/send-flight.ts`); a lista
também recorta pelo `overflow` da área de rolagem. Conferir com `document.elementFromPoint` na borda de baixo do
enfeite, como o `chat-look.spec.ts`.
**Evidência:** capturas antes (emoji cortado ao meio) e depois; `chat-look.spec.ts` confere que a pílula fica dentro
da linha e que o ponto na borda dela é a própria pílula (desktop e celular); commits `e9606ce`, `23ffb46` e
`aaa93b0` (2026-10-27).
