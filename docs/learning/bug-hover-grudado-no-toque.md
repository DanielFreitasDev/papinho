---
name: Hover grudado no toque revelava o modo privado de vez
description: no celular o item tocado fica em :hover até tocar em outro lugar; regra de revelar com :hover precisa de @media (hover: hover)
type: project
category: bug
date: 2026-10-16
---
Em tela de toque, o navegador deixa o último elemento tocado em `:hover` até a pessoa tocar em outro lugar. Uma
regra que revela conteúdo com `:hover` vira "revelar com qualquer toque, para sempre".

**Problema/Contexto:** o modo privado (PV-01) revelava com `html[data-blur] [data-private]:hover`. No celular,
depois do toque longo (que põe `data-revealed` e o tira 2,5 s depois de soltar), a mensagem continuava nítida: o
`:hover` grudado mantinha `filter: none`. Num aparelho de verdade, até um toque curto revelaria.
**Por que importa:** o diferencial do produto é a privacidade visual; no celular ela não se sustentava.
**Como aplicar:** efeitos de `:hover` que mostram ou escondem algo ficam dentro de `@media (hover: hover)`
(RESPONSIVENESS §17); no toque, use o gesto próprio (toque longo) ou o foco. A emulação do Playwright (Pixel 7,
`hasTouch`) dá `(hover: hover)` falso e reproduz o hover grudado com toque de verdade pelo CDP
(`Input.dispatchTouchEvent`), não com `locator.tap()` só.
**Evidência:** e2e `privacy.spec.ts` no projeto celular falhava com o filtro "none" depois de soltar o toque;
verde com a regra em `@media (hover: hover)` (`src/index.css`), 2026-10-16.
