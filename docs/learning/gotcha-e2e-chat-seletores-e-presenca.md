---
name: E2E do chat — hasText é substring, opacidade conta como visível, presença antes de aceitar
description: armadilhas encontradas ao testar a conversa entre dois navegadores
type: project
category: gotcha
date: 2026-10-15
---
- `locator(..., { hasText })` é substring sem caixa e inclui a citação de uma resposta: localize a linha pela
  mensagem própria (`filter({ has: page.locator("p", { hasText }) })`).
- Texto com `opacity-0` (spoiler escondido) é "visível" para o Playwright: teste o botão do spoiler, não o texto.
- A presença buscada enquanto a conversa ainda é solicitação volta 404 (CT-02) e ficava em cache; o aviso
  `conversation` (aceitar) agora invalida as consultas de presença.
- `aria-label` em `span` sem papel é proibido (axe); use texto `sr-only` dentro do badge.
- O fluxo com dois navegadores passa dos 30 s sob carga paralela: `test.slow()`.

**Evidência:** falhas e correções em `e2e/chat.spec.ts`; suíte 25/25 em 2026-10-15.
