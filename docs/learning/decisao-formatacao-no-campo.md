---
name: Formatação no campo de mensagem com um espelho por baixo do textarea
description: marcadores do WhatsApp (não Markdown), texto formatado ao vivo por um espelho que não muda a largura das letras, insertText para o desfazer e a prova letra por letra com caretPositionFromPoint
type: project
category: decision
date: 2026-10-28
---
O campo continua um `<textarea>`: o texto dele fica transparente e um espelho por baixo desenha a formatação, letra
por letra no mesmo lugar. Cursor, seleção, correção ortográfica, IME e teclado virtual seguem sendo do navegador.

**Problema/Contexto:** o dono pediu negrito, itálico, código etc. no campo, como no WhatsApp Web, com a barra flutuante
na seleção e as opções no botão direito (2026-10-28), e deixou o formato a critério ("se for melhor, Markdown").
**Escolhas e por quê:**
- **Marcadores do WhatsApp** (`*negrito*`, `_itálico_`, `~tachado~`, `` `código` ``, `||spoiler||`, ```` ``` ````,
  "> ", "- ", "1. "), os mesmos do MS-01: no Markdown `*x*` é itálico, e as mensagens já enviadas mudariam.
- **Espelho, não editor rico:** um contenteditable (Lexical, TipTap) trocaria o campo inteiro (rascunho, emojis, Enter,
  ↑, colar arquivo, voo da mensagem) e traria IME e teclado do celular como riscos. O espelho só funciona se nada mudar
  a largura das letras: negrito é contorno (`-webkit-text-stroke`), itálico é o sintetizado (a Geist não tem face
  itálica; se um dia tiver, as larguras mudam), código e spoiler têm fundo sem respiro, links e menções só mudam a cor.
  Os dois elementos têm `scrollbar-gutter: stable`, para a barra de rolagem não estreitar só um deles.
- **Texto transparente por `-webkit-text-fill-color`**, não por `color`: o axe continua lendo a cor do texto; o
  placeholder precisa da própria cor de preenchimento; em cores forçadas o espelho some e o textarea volta a pintar.
- **Edição por `execCommand("insertText")`**: entra no desfazer do navegador e dispara o input do React. O input que
  ela gera é `insertText`, então a troca de ":)" por emoji consulta `replacingText()` para não agir.
- **Barra** posicionada pelo `Range` no espelho (o textarea não diz onde cada letra está), em portal e fixa; os botões
  não tomam o foco (`preventDefault` no pointerdown e no mousedown). No toque ela sobe 64 px para caber o menu de
  copiar/colar do sistema entre ela e o texto (não conferido em aparelho real).
- **Botão direito** abre o menu próprio; Shift + botão direito deixa o do navegador (correção ortográfica), e no Firefox
  o Shift nem chega à página.
**Como aplicar:** toda classe nova no espelho precisa manter a largura das letras (nada de `font-bold`, `font-mono`,
padding ou margem em trecho). Num teste, botão direito fora da seleção move o cursor para o ponto do clique: clique
sobre o texto selecionado, medindo o ponto no espelho.
**Evidência:** `e2e/composer.spec.ts` confere, no Chromium (desktop e celular) e no Firefox, que debaixo de cada palavra
desenhada no espelho está a mesma letra do textarea (`document.caretPositionFromPoint`) e a mesma altura com as
quebras de linha; com `font-bold` no lugar do contorno o teste reprova (letras 2 a 8 posições fora) nos dois motores.
Vitest: `compose.test.ts` (18) e `parse.test.ts` (posições). Commits `1a2024d` e `9ea05b2` (2026-10-28).
