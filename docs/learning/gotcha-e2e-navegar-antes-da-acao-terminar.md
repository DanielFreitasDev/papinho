---
name: E2E — navegar antes de a ação do app terminar
description: page.goto logo depois de um clique que dispara fetch e navegação cancela o fetch no navegador, mas o servidor ainda pode processá-lo; espere o estado final do próprio app
type: project
category: gotcha
date: 2026-10-25
---
- O "Sair" do menu roda as limpezas locais, faz o `POST /api/v1/auth/logout` e só então navega para `/login`. O
  `accounts.spec.ts` clicava em "Sair" e já fazia `page.goto("/login")`, e a navegação cancelava o POST no navegador
  (status -1 no trace). Se o servidor ainda processava o POST, a página nova pegava a sessão viva em
  `/api/v1/auth/session` (200), abria a lista e logo depois só recebia 401. Se o POST nem chegava, a sessão continuava.
  Nos dois casos o login nunca aparecia.
- Correção: `logoutThroughUi` (em `e2e/support.ts`) espera a URL `/login` que o próprio app abre, como o `pwa.spec.ts`
  já fazia. Vale para qualquer ação que termina navegando: espere o estado final do app em vez de navegar por cima.
- Para reproduzir, segure o POST com `page.route` por 500 ms: o fluxo antigo falha sempre.
- O app nesse estado: o cliente do WebSocket só trata o fim de sessão (código 4401) numa conexão aberta. Se a conexão é
  recusada já no handshake (401), ele tenta de novo a cada 2 s ("Sem conexão"), e o `AppShell` com `me` nulo renderiza
  vazio em vez de ir ao login.

**Evidência:** run 40202205784 (PR #4, sem relação com a mudança dele): 1 falha e 1 flaky no `mobile-chromium`, com o
trace mostrando o POST cancelado, a sessão 200 e depois os 401. Localmente, com o POST atrasado 500 ms, o fluxo antigo
falhou e o novo passou no desktop e no celular, e o `accounts.spec.ts` passou 10/10 (2026-10-25).
