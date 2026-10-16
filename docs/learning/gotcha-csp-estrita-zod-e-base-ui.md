---
name: CSP estrita quebra zod (JIT), componentes que injetam <style> e o cache de WASM do Transformers.js
description: zod 4 usa new Function, Base UI/input-otp injetam <style> e o Transformers.js importa o runtime de blob:; com a CSP estrita viram violações
type: project
category: gotcha
date: 2026-10-15
updated: 2026-10-16
---
Com a CSP do Papinho (sem `unsafe-eval` nem `unsafe-inline` em `style-src`):

- **zod 4** compila validadores com `new Function` na primeira validação → violação de `script-src`.
  Solução: `z.config({ jitless: true })` em `src/lib/zod.ts`; o ESLint proíbe importar `zod` direto
  (`no-restricted-imports`), sempre `import { z } from "@/lib/zod"`.
- **Base UI** injeta `<style>` (ex.: esconder a barra de rolagem do ScrollArea) → violação de `style-src`.
  Solução: `<CSPProvider disableStyleElements>` em `providers.tsx` e a classe equivalente em `index.css`
  (`base-ui-disable-scrollbar`).
- **input-otp** (o InputOTP do shadcn) injeta `<style>` sem opção de desligar → substituído pelo
  `components/otp-input.tsx` (um `<input>` com `inputMode="numeric"` e `autocomplete="one-time-code"`).
- Um ThemeProvider que aplica `style` inline no `<html>` também viola; o nosso só troca classes.
- **Transformers.js 4** (Whisper no navegador, AU-04), com `env.useWasmCache` ligado (o padrão), baixa o `.mjs` do
  onnxruntime, reescreve o código e o importa de uma URL `blob:` → violação de `script-src` no worker e a transcrição
  falha com "Não foi possível transcrever". Solução: `env.useWasmCache = false` em `whisper.worker.ts`; o runtime
  vem do próprio Papinho (`/assets/**`, cache HTTP de um ano) e o modelo continua no Cache Storage do navegador. A
  violação aparece no trace do Playwright como pedido `blob:` com `_failureText: "csp"`, não no console da página.

**Como aplicar:** componente novo de terceiros → rode o e2e (o fixture falha em qualquer violação de CSP
no console) antes de dar a tela por pronta.
**Evidência:** e2e `accounts.spec.ts` falhava com "Refused to evaluate a string as JavaScript" e "Refused to
apply inline style"; verde depois das três mudanças (2026-10-15). `whisper.spec.ts` (com internet) falhava com o
`blob:` recusado e transcreve o áudio de teste com `useWasmCache = false` (2026-10-16).
