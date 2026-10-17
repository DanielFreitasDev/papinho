---
name: Frontend — salto, service worker, alvos de toque e foco (auditoria de 2026-10-17)
description: scrollToMessage com item ainda não registrado, workbox.register sem catch, data-slot trocado pelo Base UI, TabsContent sem foco
type: project
category: gotcha
date: 2026-10-17
---
- O `scrollToMessage` do message-scroller não faz nada se o item ainda não está registrado (a página recém-
  carregada leva mais de um quadro) e calcula o destino uma vez só; com `content-visibility` a posição muda no
  caminho. O salto espera o item existir e corrige a posição por até 3 s (achado A11).
- `workbox.register()` sem `.catch` vira "pageerror: The operation was aborted." no Firefox (e "Fetch API cannot
  load ... access control checks" no WebKit) quando a aba fecha logo depois de abrir "/" (A33). O fixture do e2e
  agora registra a pilha do erro de página.
- Um botão com `data-slot="button"` deixa de casar o seletor quando vira gatilho de Tooltip, menu ou diálogo (o
  Base UI troca o `data-slot`): regras de tamanho, como os 44 px em `pointer: coarse`, vão no `buttonVariants`
  (A34).
- O `TabsContent` do Base UI entra na ordem do Tab e o registry o deixa com `outline-none`: sem anel de foco
  visível (A37).
- Rolagem horizontal dentro de um contêiner (não no `documentElement`) passava no teste de 320 px; listas feitas
  para rolar de lado se marcam com `data-scroll-x` e o resto é conferido (A36).
- Paridade de chaves entre `pt-BR.json` e `en.json` não pega chave apagada dos dois: o teste confere que toda
  chave literal usada no código existe (A38).
- Para subir uma segunda instância do e2e em outras portas: a passkey exige
  `--app.identity.webauthn.allowed-origins` com a porta (o padrão aceita só a 8080), e no perfil lan a porta HTTPS
  vem de `PAPINHO_HTTPS_PORT`, não de `public-https-port`.

**Evidência:** commits `33c1708` (A11), `924128c` (A33), `7bd7889` (A34), `301360e` (A36), `27cd513` (A37),
`8f48a89` (A38), cada um com o spec ou teste que falhava antes e a mutação que o derruba.
