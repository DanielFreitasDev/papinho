---
name: Máquina local do dono — proxy no Maven, portas ocupadas e /tmp
description: proxy do settings.xml virava argumento do vite (sem proxy em 2026-10-24); 5432 ocupada; /tmp some no reboot; conta do gh (push só no repositório novo); 12 workers e ambiente e2e local
type: project
category: gotcha
date: 2026-10-16
updated: 2026-10-24
---
Diferenças da máquina local em relação ao sandbox de nuvem das passadas 1 a 6:

- Em 2026-10-24 a máquina estava sem proxy: sem `~/.m2/settings.xml`, nada escutando em 3128 e acesso direto ao
  Maven Central e ao registro do npm (o `docker build` da imagem baixa as dependências direto). Quando o proxy
  volta, valem as duas notas abaixo; confira antes com `ss -ltn | grep 3128`.
- O `~/.m2/settings.xml` tinha proxy. O `frontend-maven-plugin` repassa `--proxy/--https-proxy/--noproxy` a todo
  comando do pnpm; no `pnpm run build` eles viram argumentos do vite ("Unknown option --httpsProxy"). O passo de
  build desliga `pnpmInheritsProxyConfigFromMaven` (o `pnpm install` continua usando o proxy). Para compilar um
  commit antigo: `-Dfrontend.pnpm.pnpmInheritsProxyConfigFromMaven=false`.
- A porta 5432 já está em uso (outro PostgreSQL): o e2e local usa contêineres próprios em portas altas
  (Postgres 55432, Mailpit 51025/58025, JAR em 18080 e perfil lan em 18443/18081), sem tocar nos contêineres que já
  existem. Com o JAR fora da 8080, declare `PAPINHO_ALLOWED_ORIGINS` com a porta usada, senão o passkey falha.
- O `/tmp` é limpo no reboot (scratchpad, logs, PKI de teste e cópias do JAR somem); um reinício do Docker para
  todos os contêineres sem política de reinício.
- Até a passada 11, o `gh` estava logado numa conta só com leitura no repositório antigo (`labdev2025-cmd/papinho`):
  commits ficavam locais até alguém com escrita dar `git push origin main`. Desde a mudança do `origin` para
  `DanielFreitasDev/papinho` (privado), a conta do `gh` desta máquina tem `push` e `admin` nele (2026-10-24). Com um token da conta `labdev2025-cmd` dado pelo dono, o push sai com um credential helper
  de um comando só (`git -c credential.helper= -c 'credential.helper=!f() {...}; f' push`), sem gravar nada no git
  config nem mexer no `gh`; o token fica num arquivo 0600 temporário, apagado no fim (passada 8).
- O servidor usa threads virtuais: `jstack` não as mostra; use `jcmd <pid> Thread.dump_to_file -format=text`.
- Com o proxy ligado, a internet só sai por ele (`127.0.0.1:3128`, nas variáveis `*_proxy`); conexão direta não passa. O JVM
  ignora essas variáveis. No e2e, o Chromium só precisa de internet no `whisper.spec.ts`, que recebe o proxy por
  `PAPINHO_E2E_PROXY` (a instância local continua direta pelo `bypass`).
- O Playwright usa por padrão metade dos núcleos (12 workers aqui; 2 no runner do CI): fluxos longos estouram os
  30 s padrão e corridas de teste aparecem aqui antes do CI. Fluxo longo ganha `test.slow()`.
- Os navegadores em `~/.cache/ms-playwright` podem ser de uma versão anterior do `@playwright/test` do lockfile
  ("Executable doesn't exist at .../chromium_headless_shell-1248"): `pnpm exec playwright install chromium
  chromium-headless-shell firefox webkit` em `frontend/` baixa os certos pelo proxy (passada 12).
- Ambiente e2e local que reproduz o CI: PostgreSQL 18 num contêiner próprio em 55432, `pki init --ip 127.0.0.1
  --network 127.0.0.0/8` num `PAPINHO_PKI_DIR` temporário e as duas instâncias com o **mesmo** `PAPINHO_DATA_DIR`:
  padrão com `--server.port=18080` e `PAPINHO_ALLOWED_ORIGINS=http://localhost:18080`; lan com `--server.port=18443
  --app.pki.http-port=18081 --app.pki.public-http-port=18081 --app.pki.public-https-port=18443` e
  `PAPINHO_ALLOWED_ORIGINS=https://127.0.0.1:18443`; `PAPINHO_RATE_LIMIT_MULTIPLIER=10` nas duas. Os specs rodam com
  `PAPINHO_E2E_URL`, `PAPINHO_E2E_SETUP_URL` e `PAPINHO_E2E_HTTPS_URL` apontando para essas portas. A imagem do
  Mailpit não está no cache local: o `email.spec.ts` precisa dela (passada 12).

**Evidência:** build falhando no `pnpm-build` e verde depois de `0434f61`; `403 Permission denied` no push (de novo
na passada 8, também sem chave SSH autorizada); dump com `jcmd` mostrando requisições esperando o pool do Hikari
durante um reinício do Docker (2026-10-16); `curl --noproxy '*'` para a internet sem resposta e 200 pelo proxy;
`email.spec.ts` passando dos 30 s com 12 workers e verde com `test.slow()` (passada 8).
