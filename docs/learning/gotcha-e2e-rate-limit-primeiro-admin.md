---
name: E2E contra instância reaproveitada esbarra no rate limit e na corrida do primeiro admin
description: buckets do Bucket4j vivem na memória do processo; projetos paralelos disputavam o primeiro cadastro
type: project
category: gotcha
date: 2026-10-15
---
- O rate limit (Bucket4j em memória, ex.: 20 logins por IP a cada 15 min) não zera entre execuções do e2e
  contra o mesmo processo: a segunda rodada seguida recebe "Aguarde N segundos". Rode o e2e contra uma
  instância recém-iniciada (o CI já faz isso; localmente reinicie o JAR e recrie o banco).
- Desktop e celular rodam em paralelo; numa instância vazia os dois tentavam criar o primeiro admin. O
  projeto `primeiro-admin` (`e2e/first-admin.setup.ts`) cria a conta uma vez e os outros dependem dele.
- `locator.isVisible()` não espera: use `expect(a.or(b).first()).toBeVisible()` antes de decidir o caminho.

**Evidência:** falhas "Aguarde 26 segundos" e "dialog Guarde seus códigos não encontrado"; 23/23 depois das
mudanças, numa instância nova (2026-10-15).
