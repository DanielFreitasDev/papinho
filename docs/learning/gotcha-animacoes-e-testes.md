---
name: Animações nos testes e no roteador
description: CDP desacelera o relógio do requestAnimationFrame mas não os timers; location muda antes da view transition; toque longo com deslize não gera clique; caixa medida durante a escala
type: project
category: gotcha
date: 2026-10-24
---
Pegadinhas achadas ao animar a interface (Fase 4):

- **`Animation.setPlaybackRate` do CDP** (câmera lenta nos quadros de revisão) desacelera a linha do tempo do
  documento, que também dá o tempo do quadro do `requestAnimationFrame`, mas não `performance.now()` nem
  `setTimeout`. Um laço que comparava o tempo do quadro com `performance.now()` ficou segundos sem desenhar a onda;
  um tempo de reserva em `setTimeout` cortava a lixeira no meio. Use um relógio só e, na reserva, confira
  `getAnimations()` antes de forçar o fim.
- **View transition do TanStack Router:** `state.location` muda no início da navegação, antes da captura da tela
  antiga; `state.resolvedLocation` muda junto com o conteúdo da rota. O layout do celular (lista ou conversa) lia
  `location` e as duas capturas saíam iguais (a lista "voltava" por cima dela mesma). Layout que depende da rota lê
  `resolvedLocation ?? location`.
- **Toque longo com deslize não gera `click`:** a marca "ignorar o clique que vem depois do toque longo" ficava ligada
  e o próximo toque no microfone se perdia. Ela é zerada no início de cada interação (`pointerdown`, `keydown`).
- **`boundingBox()` durante uma animação de escala** devolve a caixa transformada (menor e deslocada). Antes de medir
  posição, espere `element.getAnimations().map((a) => a.finished)`.

**Evidência:** quadros de revisão em câmera lenta (specs temporários contra o servidor do Vite) com a onda vazia, a
lixeira cortada a 20% e as duas capturas iguais na volta do celular antes das correções, e completos depois;
`e2e/animations.spec.ts` no celular falhava no toque seguinte ao deslize antes da correção (2026-10-24).
