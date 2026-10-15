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

- O axe rodando com um diálogo ainda em fade-in mede o contraste com opacidade parcial (falso 2,9:1): o
  `expectNoSeriousA11yViolations` espera, até 2 s, as animações finitas em andamento.
- Texto decorativo com `aria-hidden` ainda entra no `color-contrast` do axe; marca d'água sobre mídia vai em
  SVG (`<pattern>` com `<text>`), que o axe não mede.
- O vermelho `destructive` padrão (oklch 0.577) dá 3,8:1 no botão destrutivo sobre o próprio fundo tingido;
  foi para 0.5.
- `twoFriends(tag)` cadastra `gil.<tag>`/`hana.<tag>`: cada spec precisa de um sufixo próprio na tag, senão a
  suíte completa falha no cadastro (rodando um spec sozinho passa).

**Evidência:** falhas do axe e dos seletores em `media.spec.ts`, `audio.spec.ts` e `moderation.spec.ts`;
29/29 verdes depois, 2026-10-15.
