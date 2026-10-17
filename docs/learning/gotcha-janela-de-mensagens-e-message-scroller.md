---
name: Janela de mensagens sobre o MessageScroller do shadcn
description: âncora que volta ao DOM faz a lista saltar, rootMargin sem root, observador rearmado antes do commit, lado do corte, compensação pela linha visível
type: project
category: gotcha
date: 2026-10-17
---
A lista da conversa desenha só uma janela das mensagens carregadas (`features/chat/message-window.ts`, achado A31,
UI-04). Montar isso sobre o `MessageScroller` do `@shadcn/react` com `content-visibility` teve estas pegadinhas:

- **Âncora que volta ao DOM:** o `MessageScroller` rola até todo item com `scrollAnchor` que **entra** no DOM (o
  recurso de "nova resposta chegou"). A linha da primeira não lida tem âncora; a janela a tira e devolve ao rolar, e
  a lista saltava até ela (1 a 7 mil px no e2e). A âncora só vale enquanto a janela é a da abertura.
- **IntersectionObserver sem `root`:** a `rootMargin` vale contra a página; dentro do contêiner que rola, a sentinela
  só disparava já visível e a rolagem batia num "fim" falso (passos de 250 px cortados em 132-169 px). Use `root` =
  o viewport da lista.
- **Observador rearmado antes do commit:** o truque de `unobserve`/`observe` no `.finally` da carga dava o aviso
  seguinte com o layout de antes do React desenhar a janela nova: revelava duas vezes, cortava o lado errado e
  saltava ~1100 px. O observador é recriado num efeito que depende do intervalo desenhado (depois do commit).
- **Lado do corte:** decidir pela posição (`scrollTop` < metade) falha, porque as linhas recém-reveladas ainda têm a
  altura estimada (10rem, ~3x a real) e a metade fica no lugar errado; o corte levava o alvo do salto ou o que a
  pessoa lia. O lado vem da borda revelada por último (no fim da lista, mantém as mais novas).
- **Compensar o corte e o que entra acima:** a referência é a primeira linha visível que fica, medida antes e no
  `useLayoutEffect` do commit; a última linha da janela não serve (as de baixo da tela trocam a altura estimada pela
  real no meio). A compensação só vale se o `scrollTop` não mudou desde a medida: se a pessoa rolou ou o
  `overflow-anchor` do navegador já ajustou, não mexe (sem somar com o Chromium).
- **WebKit sem `overflow-anchor`:** subir por linhas ainda não desenhadas puxa a lista quando a altura estimada vira a
  real (medido até ~1800 px por passo no Chromium com `overflowAnchor = "none"`); isso já acontecia com as páginas
  antigas antes da janela. O app compensa só as 50 linhas que ele mesmo põe acima (~8200 px estimados).
- **Teste:** o e2e rola em passos com `overflowAnchor = "none"` (o Chromium ancora sozinho e esconderia a falta da
  compensação) e com ele ligado, e exige que a linha visível não saia da janela: sem isso, um salto seguido de corte
  passava despercebido.

**Evidência:** `reply-jump.spec.ts` e `message-window.test.ts`; as mutações de cada item acima derrubam um dos dois.
`perf.spec.ts` com 5000 mensagens: 100 no DOM, 2084 nós, 88 MB de heap, maior tarefa longa 132 ms (antes 5000, 90
mil nós, 885 MB, 1,87 s).
