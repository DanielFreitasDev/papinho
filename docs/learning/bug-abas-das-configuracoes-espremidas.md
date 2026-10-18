---
name: Abas das configurações sumiam na Privacidade
description: item com overflow dentro de um flex-col perde a altura mínima e é espremido quando o resto passa da altura do contêiner; use shrink-0
type: project
category: bug
date: 2026-10-18
---
Num contêiner flex em coluna, cada item tem `min-height: auto` (não encolhe abaixo do conteúdo), **exceto** quando o
item tem `overflow` diferente de `visible`: aí o mínimo vira 0 e ele é o único que o flexbox consegue espremer.

**Problema/Contexto:** o `<main>` do `AppShell` é `flex flex-col overflow-y-auto`. A `<nav>` das seções de
Configurações tem `overflow-x-auto` (rola de lado no celular), o que também torna o `overflow-y` `auto`. Na Privacidade,
a única seção mais alta que a tela, a nav encolhia até sobrar só o `pt-3` (12 px de altura para 46 px de conteúdo):
as abas ficavam escondidas dentro dela, e voltar no navegador (para uma seção curta) as trazia de volta.
**Correção:** `shrink-0` na nav (`frontend/src/routes/_app/settings.tsx`).
**Como aplicar:** todo filho direto de um flex em coluna que tenha `overflow-*` e não deva encolher (barra de abas,
filtros, faixa que rola de lado) ganha `shrink-0`. `toBeVisible` do Playwright não pega isso: compare
`scrollHeight` com `clientHeight` do elemento.

**Evidência:** medição no Chromium em /settings/privacy (1600×900): nav com 12 px e `scrollHeight` 46; com
`flex-shrink: 0`, 46/46. Asserção nova em `e2e/privacy.spec.ts` (teste do bloqueio por inatividade), verde em
desktop-chromium e mobile-chromium.
