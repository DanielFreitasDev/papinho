---
name: E2E contra instância reaproveitada esbarra no rate limit e na corrida do primeiro admin
description: buckets do Bucket4j vivem na memória do processo; projetos paralelos disputavam o primeiro cadastro; a suíte passou de 100 cadastros por IP (multiplicador 20)
type: project
category: gotcha
date: 2026-10-15
updated: 2026-10-28
---
- O rate limit (Bucket4j em memória, ex.: 20 logins por IP a cada 15 min) não zera entre execuções do e2e
  contra o mesmo processo: a segunda rodada seguida recebe "Aguarde N segundos". Rode o e2e contra uma
  instância recém-iniciada (o CI já faz isso; localmente reinicie o JAR e recrie o banco).
- Desktop e celular rodam em paralelo; numa instância vazia os dois tentavam criar o primeiro admin. O
  projeto `primeiro-admin` (`e2e/first-admin.setup.ts`) cria a conta uma vez e os outros dependem dele.
- `locator.isVisible()` não espera: use `expect(a.or(b).first()).toBeVisible()` antes de decidir o caminho.
- A suíte inteira passou de 100 cadastros do mesmo IP (10 por hora × multiplicador 10): com 12 workers ela roda em
  ~11 min e os últimos specs do celular recebiam "Aguarde 18 segundos" no cadastro. Desde 2026-10-28 o e2e usa
  `PAPINHO_RATE_LIMIT_MULTIPLIER=20` (CI e receita local); spec novo que cadastra gente conta contra esse limite.

**Evidência:** falhas "Aguarde 26 segundos" e "dialog Guarde seus códigos não encontrado"; 23/23 depois das
mudanças, numa instância nova (2026-10-15). Em 2026-10-28, 10 specs do celular no fim da rodada com "Aguarde 18
segundos e tente de novo" no cadastro (multiplicador 10, 102 verdes); verdes com 20.
