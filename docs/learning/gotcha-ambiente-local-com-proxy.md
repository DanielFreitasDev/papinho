---
name: Máquina local do dono — proxy no Maven, portas ocupadas e /tmp
description: proxy do settings.xml virava argumento do vite; 5432 ocupada; /tmp some no reboot; conta do gh sem push; internet só pelo proxy; 12 workers no e2e
type: project
category: gotcha
date: 2026-10-16
updated: 2026-10-16
---
Diferenças da máquina local em relação ao sandbox de nuvem das passadas 1 a 6:

- O `~/.m2/settings.xml` tem proxy. O `frontend-maven-plugin` repassa `--proxy/--https-proxy/--noproxy` a todo
  comando do pnpm; no `pnpm run build` eles viram argumentos do vite ("Unknown option --httpsProxy"). O passo de
  build desliga `pnpmInheritsProxyConfigFromMaven` (o `pnpm install` continua usando o proxy). Para compilar um
  commit antigo: `-Dfrontend.pnpm.pnpmInheritsProxyConfigFromMaven=false`.
- A porta 5432 já está em uso (outro PostgreSQL): o e2e local usa contêineres próprios em portas altas
  (Postgres 55432, Mailpit 51025/58025, JAR em 18080 e perfil lan em 18443/18081), sem tocar nos contêineres que já
  existem. Com o JAR fora da 8080, declare `PAPINHO_ALLOWED_ORIGINS` com a porta usada, senão o passkey falha.
- O `/tmp` é limpo no reboot (scratchpad, logs, PKI de teste e cópias do JAR somem); um reinício do Docker para
  todos os contêineres sem política de reinício.
- O `gh` está logado numa conta só com leitura no repositório: commits ficam locais até alguém com escrita dar
  `git push origin main`. Com um token da conta `labdev2025-cmd` dado pelo dono, o push sai com um credential helper
  de um comando só (`git -c credential.helper= -c 'credential.helper=!f() {...}; f' push`), sem gravar nada no git
  config nem mexer no `gh`; o token fica num arquivo 0600 temporário, apagado no fim (passada 8).
- O servidor usa threads virtuais: `jstack` não as mostra; use `jcmd <pid> Thread.dump_to_file -format=text`.
- A internet só sai pelo proxy local (`127.0.0.1:3128`, nas variáveis `*_proxy`); conexão direta não passa. O JVM
  ignora essas variáveis. No e2e, o Chromium só precisa de internet no `whisper.spec.ts`, que recebe o proxy por
  `PAPINHO_E2E_PROXY` (a instância local continua direta pelo `bypass`).
- O Playwright usa por padrão metade dos núcleos (12 workers aqui; 2 no runner do CI): fluxos longos estouram os
  30 s padrão e corridas de teste aparecem aqui antes do CI. Fluxo longo ganha `test.slow()`.

**Evidência:** build falhando no `pnpm-build` e verde depois de `0434f61`; `403 Permission denied` no push (de novo
na passada 8, também sem chave SSH autorizada); dump com `jcmd` mostrando requisições esperando o pool do Hikari
durante um reinício do Docker (2026-10-16); `curl --noproxy '*'` para a internet sem resposta e 200 pelo proxy;
`email.spec.ts` passando dos 30 s com 12 workers e verde com `test.slow()` (passada 8).
