---
name: Animações sem biblioteca, com tokens e só ao vivo (Fase 4)
description: tokens de duração e curva no index.css, nenhuma dependência nova, o que anima e quando, entrada decidida antes de pintar, chave estável da bolha e movimento reduzido
type: project
category: decision
date: 2026-10-24
---
Como a interface ficou "viva" sem pesar no bundle nem na lista de mensagens:

- **Sem biblioteca:** CSS (keyframes do `index.css` e utilitários do `tw-animate-css`, que o shadcn já usava), a Web
  Animations API e as view transitions do navegador pelo TanStack Router. O Motion custaria de ~20 KB (LazyMotion) a
  ~34 KB gzip num bundle que já passa do aviso de 500 KB, e o `popLayout` dele injeta `<style>`, barrado pela CSP. O
  `<ViewTransition>` do React 19.3 ficou de fora da lista: a view transition captura o documento inteiro e segura os
  cliques enquanto anima (cada mensagem nova congelaria a tela), e as atualizações do TanStack Query chegam por
  `useSyncExternalStore`, que nunca é transition.
- **Tokens:** `--duration-instant/fast/base/slow/long` e `--ease-standard/enter/exit/spring` (a mola é um `linear()`
  gerado da equação de uma mola com 8% de passagem). Nas classes, a forma de variável (`duration-(--duration-fast)`),
  como no espaçamento; em JS, `motionMs`/`motionEase` de `lib/motion.ts` leem o mesmo valor do CSS (a Web Animations
  API não aceita `var()`). `--default-transition-*` faz todo `transition-*` seguir os tokens.
- **Movimento reduzido:** uma regra global leva toda animação e transição ao fim em 1 ms e tira as das view
  transitions; os atrasos de transição ficam (o modo privado usa o atraso para revelar). O que espera animação em JS
  (lixeira, onda, rolagem suave) consulta `prefersReducedMotion()`.
- **Mensagem que entra:** só a que chega ao vivo no fim da conversa (seq maior que tudo o que já havia, janela no
  fim, criada há menos de 15 s) e a bolha própria ao sair do campo. A decisão é da conversa, pedida por cada linha
  num `useLayoutEffect` de montagem (antes de pintar; na renderização o React Compiler não deixa ler ref): o efeito
  da linha nova roda antes do da conversa no mesmo commit, então ela compara com o estado do commit anterior. Linha
  que volta à janela de renderização não entra de novo.
- **Chave estável da bolha:** `senderId:clientMessageId` da pendente à confirmada (o banco garante o par único; o
  clientMessageId sozinho pode repetir entre remetentes de propósito). A linha não remonta na confirmação: o tique
  muda no lugar com o "pop" e a entrada não se repete.
- **"Pop" só depois de montar:** tique, contagem de não lidas, reação e troca microfone/enviar guardam o valor da
  montagem (`useState(valor)`) e animam só quando ele muda, com `key` no valor para a animação recomeçar.
- **Gravação:** a lixeira é uma sequência de três keyframes com a mesma duração (as porcentagens são o roteiro); o
  fim vem do `animationend`, e o tempo de reserva só força a volta se `getAnimations()` disser que nada está
  rodando. A onda é um canvas na resolução da tela com o nível em dB (piso -50, teto -10) do
  `getByteTimeDomainData`.

**Evidência:** `e2e/animations.spec.ts` (desktop e celular), `recording-wave.test.ts`, quadros em câmera lenta da
lixeira, do "pop", das entradas, da lista (FLIP medido por `getAnimations()`), da troca de tela e do tema
(2026-10-24).
