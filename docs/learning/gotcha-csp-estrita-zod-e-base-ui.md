---
name: CSP estrita quebra zod (JIT) e componentes que injetam <style>
description: zod 4 usa new Function e Base UI/input-otp injetam <style>; com CSP sem unsafe-eval/unsafe-inline viram violações
type: project
category: gotcha
date: 2026-10-15
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

**Como aplicar:** componente novo de terceiros → rode o e2e (o fixture falha em qualquer violação de CSP
no console) antes de dar a tela por pronta.
**Evidência:** e2e `accounts.spec.ts` falhava com "Refused to evaluate a string as JavaScript" e "Refused to
apply inline style"; verde depois das três mudanças (2026-10-15).
