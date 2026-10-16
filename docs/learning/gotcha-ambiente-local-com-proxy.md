---
name: Máquina local do dono — proxy no Maven, portas ocupadas e /tmp
description: proxy do settings.xml virava argumento do vite; 5432 ocupada; /tmp some no reboot; conta do gh sem push
type: project
category: gotcha
date: 2026-10-16
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
  `git push origin main`.
- O servidor usa threads virtuais: `jstack` não as mostra; use `jcmd <pid> Thread.dump_to_file -format=text`.

**Evidência:** build falhando no `pnpm-build` e verde depois de `0434f61`; `403 Permission denied` no push;
dump com `jcmd` mostrando requisições esperando o pool do Hikari durante um reinício do Docker (2026-10-16).
