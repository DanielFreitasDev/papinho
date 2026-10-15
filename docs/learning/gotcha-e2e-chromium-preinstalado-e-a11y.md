---
name: E2E com o Chromium preinstalado, toasts e contraste do shadcn
description: nome de download, toast com role dialog, microfone falso e cores do tema que reprovam no axe
type: project
category: gotcha
date: 2026-10-15
---
- O Playwright 1.64 pede o Chromium 1248; o sandbox tem o 1194 em `/opt/pw-browsers`. Com
  `PLAYWRIGHT_CHROMIUM_EXECUTABLE=/opt/pw-browsers/chromium-1194/chrome-linux/chrome` tudo roda, mas
  `download.suggestedFilename()` sempre devolve "download": confira o `Content-Disposition` pela API.
- Toasts do Base UI têm `role="dialog"`: `getByRole("dialog")` sem nome pega o toast "Contato adicionado".
- O microfone falso exige `--use-fake-ui-for-media-stream --use-fake-device-for-media-stream` e a permissão
  `microphone` (em `playwright.config.ts`).
- `getByRole("button", { name: "Adicionar" })` também casa "Adicionar contato": use `exact: true` e escopo.
- O `muted-foreground` padrão do shadcn (oklch 0.556) dá 4,46:1 sobre linhas em hover/`bg-muted`; foi para 0.5.
- O Base UI põe `aria-hidden` no fechar dos toasts empilhados, que continuava focável (axe
  `aria-hidden-focus`): o `ToastClose` tira da ordem de foco quando há `aria-hidden`.

**Evidência:** falhas do axe e dos seletores em `media.spec.ts`, `audio.spec.ts` e `moderation.spec.ts`;
29/29 verdes depois, 2026-10-15.
