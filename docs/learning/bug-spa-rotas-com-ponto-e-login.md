---
name: Rotas do SPA com ponto ou começando com /login davam 404/403
description: o fallback para index.html tratava qualquer ponto como arquivo e o prefixo /login capturava a rota do SPA
type: project
category: bug
date: 2026-10-15
---
- `/add/ana.silva` (@usuario com ponto) dava 404: o fallback do SPA tratava qualquer caminho com `.` como
  arquivo estático. Agora só extensões conhecidas (`STATIC_FILE` em `SpaConfig`) viram 404.
- `GET /login` caía no `denyAll`: o prefixo `/login` estava na lista de "não é SPA" por causa do
  `/login/webauthn`. A lista agora usa os caminhos exatos (`/login/webauthn`, `/webauthn/`).

**Como aplicar:** rota nova do SPA que comece igual a um endpoint de servidor → teste em `SecurityIT`
(`shouldServeSpaForRoutesWithDots`).
**Evidência:** `SecurityIT` 12/12 e e2e de contatos por link (2026-10-15).
