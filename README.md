# Papinho

Chat web **auto-hospedado**, inspirado no WhatsApp Web, com **privacidade nativa**. Você inicia na sua
máquina e usa com amigos pela rede local; depois pode levar para uma VPS com domínio e HTTPS.

Um único `papinho.jar` serve o SPA, a API e o WebSocket na mesma origem (ADR 0002), com PostgreSQL.

> Estado do projeto: em construção por fases. O progresso real, requisito por requisito e com evidência,
> está em [`STATUS.md`](STATUS.md). As decisões de arquitetura estão em [`docs/adr/`](docs/adr/).

## Stack

| Camada | Tecnologia |
|---|---|
| Backend | Java 25, Spring Boot 4.1.1, Spring Modulith 2.1.1, Spring Security 7 (sessão por cookie, passkeys), Spring Session JDBC, Flyway, BouncyCastle 1.86 |
| Banco | PostgreSQL 18 (`compose.yaml`), Testcontainers nos testes |
| Frontend | Vite 8, React 19, TypeScript 6, shadcn/ui sobre Base UI, Tailwind v4, TanStack Router e Query, react-hook-form + zod, i18next |
| Testes | JUnit 6, AssertJ, ArchUnit, Modulith; Vitest, Testing Library, msw; Playwright + axe |
| Build | Maven 3.9 (wrapper) multi-módulo; Node 24 LTS e pnpm 10 instalados pelo `frontend-maven-plugin` |

## Pré-requisitos

- JDK **25** (Temurin recomendado).
- Docker (PostgreSQL de desenvolvimento e Testcontainers).
- Para trabalhar no frontend fora do Maven: Node **24.22.0** (`frontend/.nvmrc`) e `corepack enable`.

## Desenvolvimento

```bash
# 0. Uma vez (e sempre que o frontend mudar de dependências): gera e instala o JAR do SPA
./mvnw -B install -DskipTests

# 1. Backend em http://localhost:8080 (sobe o PostgreSQL do compose.yaml sozinho)
./mvnw -pl backend -Dskip.installnodepnpm -Dskip.pnpm -Dspring-boot.run.profiles=local spring-boot:run

# 2. Frontend com recarga instantânea em http://localhost:5173 (proxy de /api, /ws e /setup para :8080)
cd frontend && corepack enable && pnpm install && pnpm dev
```

Sem Docker Compose, rode `TestPapinhoApplication` (em `backend/src/test`) pela IDE: ela sobe o PostgreSQL
com Testcontainers.

## Testes

```bash
./mvnw -B verify                    # backend: unitários, integração (Testcontainers), ArchUnit, Modulith
cd frontend && pnpm format:check && pnpm lint && pnpm typecheck && pnpm test && pnpm build

# e2e contra um papinho.jar em execução
./mvnw -B install -DskipTests
java -jar backend/target/papinho.jar &                      # precisa do PostgreSQL (variáveis abaixo)
cd frontend && pnpm exec playwright install chromium && PAPINHO_E2E_URL=http://localhost:8080 pnpm e2e
```

## Configuração

| Variável | Padrão | Uso |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/papinho` | JDBC do PostgreSQL |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | `papinho` / vazio | credenciais do banco |
| `PAPINHO_PORT` | `8080` | porta HTTP (sem o perfil `lan`) |
| `PAPINHO_PKI_DIR` | `~/.papinho/pki` | diretório da PKI da LAN (fora do repositório) |
| `PAPINHO_HTTPS_PORT` / `PAPINHO_HTTP_PORT` | `8443` / `8080` | portas de escuta no perfil `lan` |
| `PAPINHO_PUBLIC_HTTPS_PORT` / `PAPINHO_PUBLIC_HTTP_PORT` | iguais às de escuta | portas que os amigos usam (80/443 com redirecionamento) |
| `PAPINHO_HSTS` | `false` | liga HSTS (produção) |
| `PAPINHO_FORWARD_HEADERS` | `none` | `framework` atrás de proxy reverso |
| `PAPINHO_DATA_DIR` | `~/.papinho` | base de `files/` (arquivos enviados), `uploads/` (uploads em andamento) e da chave de cifra |
| `PAPINHO_STORAGE_DIR` / `PAPINHO_UPLOAD_DIR` | dentro de `PAPINHO_DATA_DIR` | troca cada um desses diretórios |
| `PAPINHO_CLAMAV_ENABLED` | `false` | analisa cada arquivo com o ClamAV (`PAPINHO_CLAMAV_HOST`, `PAPINHO_CLAMAV_PORT`, padrão `localhost:3310`) |
| `PAPINHO_LINK_PREVIEW` | `true` | prévia de links buscada pelo servidor (desligue se a instância não deve acessar a internet) |
| `PAPINHO_GIFS_PROVIDER` / `PAPINHO_GIFS_API_KEY` | `none` / vazio | GIFs pelo Tenor (`tenor` com a chave da API); a busca e os GIFs passam pelo servidor (ADR 0011) |

A cota por usuário (padrão 5 GB), as janelas de edição e exclusão e os provedores de transcrição ficam no
painel do admin. Arquivos têm limite de 1 GB e sobem por upload retomável (tus): atrás de um proxy, libere
`PATCH` em `/api/v1/uploads/**` com corpo de até 50 MB por requisição.

### Mensagens

Grupos (com admins, permissões e convite por link ou QR Code), listas de transmissão, mensagens salvas,
enquetes, figurinhas, mensagens temporárias, agendadas e de visualização única. Os jobs agendados (mensagens
temporárias, agendadas e limpeza da visualização única) usam o ShedLock: com mais de uma instância apontando para o
mesmo banco, só uma roda cada job. Figurinhas são WebP de até 1 MB enviados por qualquer pessoa da instância e
ficam disponíveis para todos.

### Privacidade

Tudo é aplicado no servidor: quem vê visto por último, online, foto e recado (com "meus contatos exceto…") e
quem pode adicionar você a grupos; quem não compartilha o visto por último ou o online não vê o dos outros, e
quem desliga a confirmação de leitura não vê a dos outros (no grupo ela vale por padrão e pode ser desligada em
cada um). Em Configurações > Privacidade também ficam:

- **Modo privado** (Alt+Shift+P): desfoca mensagens, prévias, nomes, fotos e mídia; revela com o mouse (na hora
  ou depois de alguns segundos), com o foco do teclado ou com um toque longo.
- **Bloqueio de tela**: PIN ou senha próprios, separados da senha da conta, com bloqueio por inatividade, ao
  trocar de aba ou janela e por atalho (tudo desligado por padrão). **É uma proteção visual**: a sessão continua
  ativa e quem tiver acesso às ferramentas do navegador ainda alcança os dados.
- **Conversas trancadas**: trancadas pelo painel da conversa com um código (o mesmo código em várias conversas
  abre todas juntas). Saem da lista e só abrem com o código; recarregar a página ou reiniciar o servidor tranca
  de novo. Um código esquecido não tem recuperação.

### Transcrição de áudios

Por padrão os áudios são transcritos **no navegador** (Whisper com Transformers.js): o modelo (≈80 MB no
`base`, ≈250 MB no `small`) é baixado do Hugging Face na primeira vez em cada aparelho e o áudio não sai dele.
No painel do admin, aba Transcrição, dá para cadastrar provedores no servidor compatíveis com a API da OpenAI —
Groq (`https://api.groq.com/openai`, `whisper-large-v3-turbo`, até 25 MB), um Whisper próprio (com
`hotwords` e verificação em `GET {base}/health`), a OpenAI ou uma URL qualquer — e escolher o padrão. A chave
fica cifrada no banco e nunca volta para o navegador; cada pessoa escolhe o provedor em Configurações.

## Subir na rede local (HTTPS com CA própria)

Navegadores só liberam PWA, notificações, microfone e passkeys em HTTPS. Na LAN o Papinho usa uma CA
própria **restrita por Name Constraints** ao hostname e à faixa de IP da rede: instalada nos aparelhos dos
amigos, ela não consegue validar nenhum outro site (ADR 0007).

```bash
./mvnw -B install -DskipTests

# 1. Gera a CA e o certificado do servidor em ~/.papinho/pki (chaves 0600). Mostra a impressão digital.
java -jar backend/target/papinho.jar pki init                 # opções: --hostname --ip --network
#    rodar de novo reaproveita a CA; "pki renew" reemite só o certificado; "pki status" mostra a validade

# 2. Sobe com HTTPS (8443) e a porta HTTP de instalação (8080)
DATABASE_PASSWORD=... java -jar backend/target/papinho.jar --spring.profiles.active=lan
```

Na porta HTTP só existe `/setup/**`; o resto é redirecionado para HTTPS. A chave privada da CA **nunca**
deve sair da máquina nem entrar no repositório.

### Portas 80 e 443 (endereços sem porta)

Para que os amigos usem `http://papinho.lan/setup` e `https://papinho.lan/`, redirecione as portas no
host e informe as portas públicas:

```bash
sudo iptables -t nat -A PREROUTING -p tcp --dport 80  -j REDIRECT --to-ports 8080
sudo iptables -t nat -A PREROUTING -p tcp --dport 443 -j REDIRECT --to-ports 8443
PAPINHO_PUBLIC_HTTP_PORT=80 PAPINHO_PUBLIC_HTTPS_PORT=443 java -jar backend/target/papinho.jar --spring.profiles.active=lan
```

Alternativa: `sudo setcap 'cap_net_bind_service=+ep' "$(readlink -f "$(command -v java)")"` e
`PAPINHO_HTTP_PORT=80 PAPINHO_HTTPS_PORT=443`.

### Hostname na rede

O certificado vale para o hostname (padrão `papinho.lan`) e para o IP. Use o hostname: **passkeys exigem
um nome**, porque o WebAuthn não aceita IP como RP ID. Opções:

1. **DNS do roteador** (recomendado): reserve o IP do servidor no DHCP e crie a entrada `papinho.lan →
   IP`. Funciona em todos os aparelhos. Ressalvas: nem todo roteador permite entradas locais; se o IP
   mudar, rode `pki renew --ip <novo>` (o IP precisa continuar na faixa da CA).
2. **mDNS/Avahi**: gere a PKI com `pki init --hostname papinho.local` e anuncie o nome com o Avahi
   (`avahi-daemon`, hostname da máquina `papinho`). Ressalvas: mDNS só resolve nomes `.local`; Android e
   Windows antigos resolvem `.local` de forma irregular; redes de convidados costumam bloquear multicast.
3. **`/etc/hosts`** em cada computador: não funciona em celulares sem root.

### Instalar a CA nos aparelhos

Abra `http://papinho.lan:8080/setup` (ou o QR Code mostrado lá) em cada aparelho. A página detecta o
sistema e o navegador e mostra os passos para Ubuntu/Debian, Fedora, Arch, Windows, macOS, iOS/iPadOS,
Android e Firefox. **Confira a impressão digital SHA-256** com a que o `pki init` mostrou.

No Linux, um comando instala no sistema e nas bases NSS (Chrome e Firefox), conferindo a impressão digital
antes; é idempotente:

```bash
curl -fsSL http://papinho.lan:8080/setup/install-ca.sh | sh
curl -fsSL http://papinho.lan:8080/setup/install-ca.sh | sh -s -- --uninstall
```

`scripts/verify-install-ca.sh` testa esse script em contêineres Debian, Fedora e Arch.

## Produção (VPS)

- Rode **sem** o perfil `lan`: a PKI fica desligada e `/setup` não existe. O TLS vem do proxy reverso
  (Caddy, nginx, Traefik) ou de uma CA pública.
- Atrás do proxy: `PAPINHO_FORWARD_HEADERS=framework`, `PAPINHO_HSTS=true`; o proxy precisa repassar
  WebSocket em `/ws`.
- PostgreSQL gerenciado ou em contêiner com volume e backup.
- O proxy precisa aceitar corpos de até 50 MB em `PATCH /api/v1/uploads/**` (pedaços do upload retomável).

## Backup

Pendente (BK-03, Fase 7): script com `pg_dump` mais o volume de arquivos e restauração testada.

## Guias e decisões

- [`PROMPT.md`](PROMPT.md): especificação e contrato de trabalho; [`STATUS.md`](STATUS.md): progresso.
- [`docs/adr/`](docs/adr/): decisões de arquitetura.
- Guias na raiz: `STRUCTURE.md`, `JAVA.md`, `UI.md`, `RESPONSIVENESS.md`, `COMMITS.md`, `CONTEXT.md`.
