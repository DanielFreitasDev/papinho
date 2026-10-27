---
name: Animações nos testes e no roteador
description: CDP desacelera o relógio do requestAnimationFrame mas não os timers; location muda antes da view transition; toque longo com deslize não gera clique; caixa medida durante a escala; ready da view transition interrompida vira erro não tratado; toque durante a view transition
type: project
category: gotcha
date: 2026-10-24
updated: 2026-10-27
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
  posição, espere `element.getAnimations().map((a) => a.finished)`. Vale para `getBoundingClientRect()` também: o
  `touch.spec` media o diálogo no meio da entrada (240 ms) e achava botões de 43 px no runner de 4 vCPUs. Ao esperar
  `document.getAnimations()`, deixe de fora as infinitas (`getComputedTiming().endTime === Infinity`: spinner,
  brilho do esqueleto) e as presas à rolagem (`timeline !== document.timeline`: o esmaecido das faixas com
  `scroll-fade-x`, cujo fim é uma porcentagem e não um tempo), senão a espera não acaba.
- **View transition interrompida:** quando uma começa antes de a anterior animar, o `ready` da anterior rejeita com
  AbortError ("Transition was skipped" no Chromium 156, "Old view transition aborted by new view transition" no
  WebKit 27), e os dois contam isso como erro não tratado mesmo sem ninguém ter pedido o `ready`. O TanStack Router
  só espera o `updateCallbackDone`, então o erro aparecia no console de quem navegava rápido (e reprovava e2e ao
  acaso). O `lib/motion.ts` embrulha o `document.startViewTransition` e trata o `ready` de toda transição.
- **Toque logo depois de voltar para a lista no celular** cai na view transition da troca de tela, não no elemento:
  durante a transição quem recebe o ponteiro é a camada `::view-transition`. Antes de tocar, espere as animações cujo
  `effect.pseudoElement` começa com `::view-transition` (as infinitas da lista nunca acabam, então não espere todas).
  A onda de toque e o voo da mensagem duram menos de meio segundo: o e2e os confere com o CDP a 10% (o voo é da Web
  Animations API para isso funcionar), senão o atributo some antes da checagem numa máquina ocupada (2026-10-27).

**Evidência:** quadros de revisão em câmera lenta (specs temporários contra o servidor do Vite) com a onda vazia, a
lixeira cortada a 20% e as duas capturas iguais na volta do celular antes das correções, e completos depois;
`e2e/animations.spec.ts` no celular falhava no toque seguinte ao deslize antes da correção (2026-10-24). Em
2026-10-25: sonda com os navegadores do Playwright 1.64 (duas transições seguidas: `updateCallbackDone` resolve, o
`ready` rejeitado vira pageerror nos dois motores e some com o `catch`); o teste novo do `animations.spec.ts` reprova
contra o JAR anterior com a mesma mensagem do CI (run 40141965238).
