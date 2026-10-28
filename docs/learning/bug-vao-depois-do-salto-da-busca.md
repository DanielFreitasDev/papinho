---
name: Salto para uma das últimas mensagens deixava um vão embaixo
description: o MessageScroller cria um espaçador no fim da lista para alinhar o alvo e só o tira ao rolar até o fim pelo botão; perto do fim, role até o fim em vez de centralizar
type: project
category: bug
date: 2026-10-28
---
Nunca peça ao `MessageScroller` (shadcn, `@shadcn/react`) um alinhamento que o conteúdo não alcança: para cumprir o
pedido, ele cria um espaçador depois da última linha, e o espaçador fica até alguém rolar até o fim pelo botão.

**Problema/Contexto:** o dono buscou na conversa (lupa do cabeçalho) e o resultado era a última mensagem: ela "subia"
para o meio da tela e ficava um vão embaixo, que só sumia fechando e abrindo a conversa. O `settleOn` do salto (busca,
citação, resultado da busca global) chamava `scrollToMessage(id, { align: "center" })`. No `scrollToElement` da
biblioteca, o alvo calculado passa do fim do conteúdo e ela põe a diferença na altura do
`[data-message-scroller-spacer]`; só `scrollToEnd`/`scrollToStart` zeram o espaçador. Rolar com a mão não tira o vão,
porque ele é conteúdo de verdade.
**Por que importa:** qualquer salto novo com `align` (center, start ou end) para perto do fim repete o vão; com a
conversa aberta por muito tempo, ele fica.
**Como aplicar:** antes de centralizar, confira se o centro cabe no fim real do conteúdo (a maior borda de baixo das
linhas, sem o espaçador, mais o respiro do conteúdo); se não couber, `scrollToEnd`, que também zera o espaçador e volta
a acompanhar as mensagens novas (`centerOn` em `conversation-view.tsx`). Para conferir num teste, leia o espaçador
(`hidden` e `offsetHeight`) e a distância da última linha até o fim da área visível.
**Evidência:** `e2e/search.spec.ts` ("salto da busca para a última mensagem não deixa vão embaixo") reprovava com
240 px de espaçador no celular e no desktop antes da correção e passa depois, com o spec antigo da busca e o
`reply-jump.spec.ts` verdes (`ae7e787`, 2026-10-28).
