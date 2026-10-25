---
name: E2E em Firefox e WebKit — cookies, tema, proxy e capturas
description: WebKit recusa cookie Secure em localhost, Firefox ignora colorScheme do contexto, capturas do WebKit violam a CSP, proxy do desktop, fetch cancelado vira pageerror no WebKit, WebKit lento no runner Ubuntu 26.04
type: project
category: gotcha
date: 2026-10-16
---
O spec do UI-05 (`native-controls.spec.ts`) roda em Chromium, Firefox e WebKit. O que quebrou e por quê:

- **WebKit recusa cookies `Secure` em `http://localhost`** (Chromium e Firefox aceitam). Sem o `XSRF-TOKEN`, o
  login dá 403. O projeto `desktop-webkit` usa a instância HTTPS do perfil lan (`PAPINHO_E2E_HTTPS_URL`,
  `ignoreHTTPSErrors`); ela compartilha o banco com a instância HTTP.
- **Firefox ignora `colorScheme` em `browser.newContext()`** e também o `page.emulateMedia` feito antes da primeira
  navegação. `emulateMedia` depois do `goto` funciona nos três motores (o tema "sistema" acompanha a mudança).
- **Cada `page.screenshot()` no WebKit injeta um `<style>`** que a CSP estrita do app recusa (uma violação por
  captura, inclusive com `caret: "initial"`; zero no Chromium e no Firefox, provado numa página mínima). O spec
  descarta só essas linhas, logo após as próprias capturas.
- O Safari avisa no console, como erro, que ignora `interactive-widget` da meta viewport: o fixture tolera.
- **Proxy do desktop:** o Firefox do Playwright usa o proxy do sistema (KDE) e o WebKit as variáveis `*_proxy`
  (o `no_proxy` com espaços não vale para ele); WebSocket e HTTPS local passam pelo Squid e falham. A config usa
  `network.proxy.type: 0` no Firefox e o ambiente sem `*_proxy` no WebKit.
- O Playwright 1.64 pede Chromium 1248, Firefox 1555 e WebKit 2370; versões antigas no cache não servem para
  Firefox e WebKit.
- **Fetch cancelado vira erro de página no WebKit:** um `reload()` ou `goto` com pedidos em andamento gera um
  pageerror "Fetch API cannot load <url> due to access control checks" por pedido, mesmo na mesma origem (sem CORS
  nenhum). O `networkidle` antes do reload não basta: um aviso pelo WebSocket dispara outra busca no meio. O fixture
  tolera só os da mesma origem.
- **WebKit lento no runner do CI:** desde que o `ubuntu-latest` passou para o Ubuntu 26.04 (a partir de
  2026-10-19), o login até a lista levou 2,3 s no WebKit e 5,5 s no primeiro teste da rodada (aqui, 0,4 s, também
  com `LIBGL_ALWAYS_SOFTWARE=1`; a view transition do login pesa uns 100 ms). O `expect` padrão de 5 s estourava: o
  projeto `desktop-webkit` usa 15 s.

**Pendências do WebKit no Linux (GStreamer), não do Safari:** a página congela de vez em quando com vídeo na
conversa — acontece também na versão anterior ao UI-05 (3 de 6 tentativas com o JAR de `d37a39e`), não com só
áudio e documentos (0 de 8) e não numa página mínima com vídeo; e uma mensagem nova às vezes chega ao cache (cursor
do afterSeq avança, a lista mostra a prévia) mas só aparece na conversa ao reabrir. O spec do UI-05 tira o vídeo
no WebKit e reabre a conversa antes de verificar; as duas investigações estão no STATUS.

**Evidência:** sondas e traces de 2026-10-16 (login 403 sem cookie; `matchMedia` falso no Firefox; 39 violações em
39 capturas; WebSocket do Firefox fechando com o proxy do KDE e conectando com `network.proxy.type: 0`); spec verde
nos três motores depois dos ajustes. Em 2026-10-25, traces do run 40141965238 (tentativa 2, `ubuntu-26.04`): 5
pageerrors logo depois do `reload()` do spec, nenhum WARN ou ERROR no servidor, 2.270 respostas 200; tela parada no
login de +1,8 s até o fim da espera na primeira tentativa.
