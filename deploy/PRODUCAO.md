# Produção — VPS + Cloudflare Tunnel

O Papinho de produção roda em **https://papinho.cepify.com.br**, numa VPS Linux ARM64 (Ubuntu 24.04) que também
hospeda outros serviços. Este arquivo descreve a instalação e o deploy automático. Segredos ficam só na VPS.

```
Navegador ──HTTPS──▶ Cloudflare (TLS) ──Tunnel──▶ cloudflared (VPS) ──HTTP──▶ 127.0.0.1:54326 (papinho.service)
                                                                                     │
                                                                PostgreSQL 18 local ◀┘  (banco e role papinho)
```

## Peças na VPS

| Peça | Onde |
|---|---|
| Usuário de sistema | `papinho` (sem shell, home `/var/lib/papinho`) |
| JAR | `/opt/papinho/papinho.jar` (root, 0644; o serviço só lê) |
| Dados (arquivos, uploads, `secret.key`) | `/var/lib/papinho` (papinho, 0700) |
| Configuração | `/etc/papinho/papinho.env` (root, 0600), modelo em [`papinho.env.example`](papinho.env.example) |
| Serviço | `/etc/systemd/system/papinho.service`, cópia em [`papinho.service`](papinho.service) |
| Banco | PostgreSQL 18 local, database e role `papinho` (dono), sem acesso público |
| Deploy | `/opt/papinho/deploy.sh` (root, 0755), cópia de [`deploy.sh`](deploy.sh) |
| Runner do GitHub | `~ubuntu/actions-runner-papinho`, serviço `actions.runner.DanielFreitasDev-papinho.papinho-vps`, label `papinho` |
| Sudo do runner | `/etc/sudoers.d/papinho-deploy`: `ubuntu ALL=(root) NOPASSWD: /opt/papinho/deploy.sh`; o `ubuntu` da imagem de nuvem também tem sudo sem senha para tudo, então o runner tem root (veja [Repositório público e o runner](#repositório-público-e-o-runner)) |

- O serviço escuta **só em loopback** (`SERVER_ADDRESS=127.0.0.1`, porta 54326); nenhuma porta nova foi aberta.
- Atrás do túnel: `PAPINHO_FORWARD_HEADERS=native` (o `cloudflared` conecta por loopback, que o Tomcat trata como
  proxy interno; o IP do cliente vem do `X-Forwarded-For`) e `PAPINHO_HSTS=true`.
- `PAPINHO_RP_ID` e `PAPINHO_ALLOWED_ORIGINS` precisam bater com o domínio, senão as passkeys falham.
- Perder `/var/lib/papinho/secret.key` torna ilegíveis TOTP, chaves de provedores e a chave VAPID: inclua no backup.

## Cloudflare

No túnel `cepify` (Zero Trust → Networks → Tunnels, gerenciado pelo painel), uma regra de entrada
`papinho.cepify.com.br → http://localhost:54326`, antes da regra final `http_status:404`, e um registro DNS `CNAME
papinho → <id do túnel>.cfargotunnel.com` com proxy ligado. O upload retomável manda pedaços de até 50 MB, abaixo do
limite de 100 MB por requisição do plano gratuito.

## Deploy automático

Todo push na `main` roda o CI (`.github/workflows/ci.yml`). Se os jobs `commits`, `backend`, `frontend` e `e2e`
passarem:

1. o job **deploy** roda no runner da VPS, recupera o `papinho.jar` testado no job `backend` (passado pelo cache do Actions, que tem cota própria
   de 10 GB por repositório; a cota de artifacts é da conta inteira e enche com os outros projetos) e chama
   `sudo /opt/papinho/deploy.sh <jar> <versão>`: guarda o JAR atual, instala o novo, reinicia, espera o
   `/actuator/health` responder UP (até 3 min) e confere a versão no `/actuator/info`. Se falhar, volta o JAR
   anterior e o job fica vermelho;
2. o job **release** publica a imagem `ghcr.io/danielfreitasdev/papinho` (linux/amd64 e linux/arm64; tags da versão,
   `latest` e `sha-<commit>`) com esse mesmo JAR, cria a tag anotada `vX.Y.Z` (versão do `pom.xml`) e a GitHub Release
   com `papinho.jar`, `papinho.jar.sha256`, o `docker pull` e notas geradas pelos commits. Se a versão já foi publicada,
   não cria nada: para uma release nova, suba a versão
   (`./mvnw -B versions:set -DnewVersion=1.2.0 -DgenerateBackupPoms=false` e o `version` do `frontend/package.json`).

Atualizar o `deploy.sh` na VPS depois de mudá-lo no repositório (o runner não pode reescrevê-lo):

```bash
scp deploy/deploy.sh ubuntu@<vps>:/tmp/ && ssh ubuntu@<vps> 'sudo install -o root -g root -m 0755 /tmp/deploy.sh /opt/papinho/deploy.sh'
```

## Repositório público e o runner

Desde 2026-10-25 o repositório é público (Actions grátis e sem cota). Um PR de fork pode mudar o workflow e mandar um
job para o runner da VPS, que roda como `ubuntu`, com sudo para tudo. Por isso, workflows de PRs de quem não tem
escrita só rodam com aprovação (Settings → Actions → General → "Require approval for all external contributors"; pela
API, `fork-pr-contributor-approval` com `all_external_contributors`). **Nunca aprove** um desses workflows sem ler tudo
o que o PR muda em `.github/` e nos scripts que o CI executa: aprovar um PR malicioso dá root na VPS, que hospeda
outros serviços. Os PRs do Dependabot vêm de branches do próprio repositório e rodam sem aprovação, só nos runners
do GitHub (o job de deploy é só de push na `main`).

## Operação

```bash
systemctl status papinho                     # estado
journalctl -u papinho -n 200 --no-pager      # logs
curl -s http://127.0.0.1:54326/actuator/health
sudo systemctl restart papinho
```

Backup: `scripts/backup.sh` com `PGHOST=127.0.0.1 PGUSER=papinho PGPASSWORD=... PAPINHO_DATA_DIR=/var/lib/papinho`
(veja a seção Backup do README). Antes de um deploy com migração nova do Flyway (o `deploy.sh` só volta o JAR), um
dump do banco, legível só pelo root:

```bash
sudo install -d -o root -g root -m 0700 /var/backups/papinho
sudo sh -c 'umask 077 && sudo -u postgres pg_dump --format=custom papinho > /var/backups/papinho/papinho-$(date -u +%Y%m%dT%H%M%SZ).dump'
```

## Primeiro administrador

O primeiro usuário de uma instância vazia vira admin. Para não deixar essa janela aberta na internet, a conta admin
foi criada pelo loopback da VPS (`POST /api/v1/auth/register` em `127.0.0.1:54326`) antes de o hostname entrar no
túnel. Depois disso o cadastro fica só por convite (`INVITE_ONLY`, padrão), ajustável no painel do admin.
