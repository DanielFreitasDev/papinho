---
name: Produção na VPS atrás do Cloudflare Tunnel
description: admin criado pelo loopback antes do hostname, cookie Secure no curl por http, beacon da Cloudflare barrado pela CSP, $b:r no zsh
type: project
category: gotcha
date: 2026-10-17
---
Pegadinhas da primeira publicação em https://papinho.cepify.com.br (deploy/PRODUCAO.md).

- **Primeiro admin:** o primeiro cadastro de uma instância vazia vira admin. Para não abrir essa corrida na internet,
  a conta foi criada pelo loopback da VPS (`POST /api/v1/auth/register` em `127.0.0.1:54326`) antes de a regra
  entrar no túnel. Repita isso em qualquer instância nova exposta publicamente.
- **CSRF pelo curl:** o `XSRF-TOKEN` sai com `Secure` e o curl não o devolve numa URL `http://127.0.0.1`, então o
  POST dá 403. Leia o `Set-Cookie` do GET e mande `Cookie: XSRF-TOKEN=<t>` e `X-XSRF-TOKEN: <t>` à mão.
- **Cabeçalhos do túnel:** o `cloudflared` conecta por loopback, que o `RemoteIpValve` trata como proxy interno;
  com `PAPINHO_FORWARD_HEADERS=native`, o IP do cliente vem do `X-Forwarded-For` e o esquema do
  `X-Forwarded-Proto: https`.
- **Beacon do Web Analytics:** a zona `cepify.com.br` injeta `static.cloudflareinsights.com/beacon.min.js` em toda
  página; a CSP estrita bloqueia e o console mostra um erro. É configuração da zona inteira (afeta os outros apps):
  não foi mudada. Um e2e contra a produção vai acusar esse erro de console.
- **Túnel pela API:** a configuração do túnel `cepify` é um documento único (`PUT .../cfd_tunnel/<id>/configurations`):
  releia antes de gravar, insira a regra nova antes do `http_status:404` final e compare com a leitura anterior.
- **CI no repositório privado:** ao passar para `DanielFreitasDev/papinho` (privado), o `ubuntu-latest` caiu de 4
  para 2 vCPUs e o e2e passou dos 30 min sem nenhuma falha (run 39087578514 cancelado por tempo no teste 81). O teto
  do job foi para 60 min.
- **Cota de artifacts da conta:** em repositório privado, a cota de armazenamento de artifacts é da conta
  `DanielFreitasDev` inteira (outros repositórios já a enchiam) e é recalculada a cada 6–12 h. O upload do JAR falhou
  com "Artifact storage quota has been hit" (run 39089557035) e o deploy não rodou. O JAR passa ao deploy pelo
  `actions/cache` (10 GB por repositório, chave `papinho-jar-<run_id>`) e os relatórios só sobem em falha, com
  `continue-on-error`. O Cepify ainda usa artifacts e pode esbarrar na mesma cota.
- **zsh:** em `"refs/remotes/$b:refs/heads/..."` o `:r` vira modificador do zsh e come o texto; use `${b}`.

**Evidência:** sessão de 2026-10-17; `/api/v1/admin/settings` 200 com a conta criada, login e `wss://.../ws` pelo
domínio público, os seis hostnames vizinhos respondendo 200 depois da mudança.
