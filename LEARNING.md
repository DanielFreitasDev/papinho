# LEARNING.md — base de conhecimento do projeto

Leia as entradas relacionadas à tarefa. Os detalhes ficam em `docs/learning/`.

## Bugs e soluções
- [Handler /assets/** precisa apontar para a subpasta](docs/learning/bug-handler-de-assets-sem-subpasta.md) — o exemplo do guia dava 404 em todo asset com hash.
- [Redirecionamento em builtin especial encerra o sh](docs/learning/bug-sh-redirecionamento-em-builtin-especial.md) — teste de /dev/tty sempre num subshell.
- [Login por passkey não ficava na sessão](docs/learning/bug-passkey-sessao-nao-persistia.md) — o filtro WebAuthn do Spring sai sem repositório de contexto e com outro principal.
- [Rotas do SPA com ponto ou /login davam 404/403](docs/learning/bug-spa-rotas-com-ponto-e-login.md) — só extensões conhecidas são arquivo; prefixos exatos.
- [Aviso durante uma sincronização se perdia](docs/learning/bug-sync-perdia-aviso-durante-sincronizacao.md) — deduplicar a busca, agendar mais uma rodada e esperar a primeira busca.
- [Ternário com Long nulo dá NPE](docs/learning/bug-ternario-com-long-nulo.md) — `cond ? long : null` desembrulha o null; use if/else.
- [Pool esgotado por @TransactionalEventListener](docs/learning/bug-pool-esgotado-por-transactional-event-listener.md) — o registro do Modulith pede uma 2ª conexão; use afterCommit ou @ApplicationModuleListener.
- [Aviso perdido antes da primeira conexão do WebSocket](docs/learning/bug-websocket-primeira-conexao-sem-sync.md) — sincronize em toda conexão, não só na reconexão.

## Pegadinhas de ambiente, dependências e bibliotecas
- [PKIX do JDK/BC ignora Name Constraints da âncora](docs/learning/gotcha-pkix-ignora-name-constraints-da-ancora.md) — prove a CA da LAN com openssl verify ou certutil.
- [Ambiente de nuvem: Docker, imagens e processos](docs/learning/gotcha-ambiente-nuvem-docker-e-processos.md) — iniciar dockerd, mirror.gcr.io contra 429, JDK 25, MVNW_REPOURL, cuidado com pkill -f.
- [MockMvc não serializa SameSite](docs/learning/gotcha-mockmvc-nao-serializa-samesite.md) — atributos de cookie só se provam no servidor real.
- [CSP estrita: zod JIT e componentes que injetam <style>](docs/learning/gotcha-csp-estrita-zod-e-base-ui.md) — zod jitless via @/lib/zod, CSPProvider do Base UI, sem input-otp.
- [E2E: rate limit em memória e corrida do primeiro admin](docs/learning/gotcha-e2e-rate-limit-primeiro-admin.md) — instância nova por rodada; projeto de setup; `PAPINHO_RATE_LIMIT_MULTIPLIER` no e2e.
- [STOMP com Spring Security 7](docs/learning/gotcha-stomp-spring-security-csrf-e-heartbeat.md) — CSRF cru no CONNECT, agendador do heartbeat, WebSocket fecha com a sessão HTTP.
- [E2E do chat](docs/learning/gotcha-e2e-chat-seletores-e-presenca.md) — hasText é substring, opacidade é visível, presença antes de aceitar.
- [Mídia: ImageIO, Uppy, MockMvc, TRUNCATE, onnxruntime](docs/learning/gotcha-midia-imagem-e-testes.md) — EXIF por segmentos, sem destroy no sucesso, URL crua no MockMvc.
- [E2E: Chromium preinstalado, toasts e contraste](docs/learning/gotcha-e2e-chromium-preinstalado-e-a11y.md) — nome de download, headless shell sem notificações, toast é dialog, muted 0.5, axe em animação, tag por spec, Spinner/Item do shadcn, erros forçados só 4xx.
- [PWA, Web Push, busca e e2e offline](docs/learning/gotcha-pwa-push-e-busca.md) — stemmer pt, ponto EC sem checagem no JDK, MIME do manifesto, tsc -b com e2e.
- [E-mail de recuperação: executor, ArchUnit e SMTP nos testes](docs/learning/gotcha-email-recuperacao-executor-e-testes.md) — sem applicationTaskExecutor com WebSocket, nada de token no outbox, GreenMail/Mailpit.

## Decisões de arquitetura
- As decisões vivem em `docs/adr/` (0001–0014).
- [Fase 4: grupos, transmissão, temporárias, visualização única, figurinhas](docs/learning/decisao-fase4-mensagens-avancadas.md) — limites e escolhas que o código não diz.
- [Fase 5: privacidade](docs/learning/decisao-fase5-privacidade.md) — reciprocidade por par, leitura no grupo, token das trancadas, cache offline.
- [Fase 6: lista, busca, notificações e offline](docs/learning/decisao-fase6-organizacao-notificacoes.md) — arquivo calculado, apagar com removed_seq, push decidido no servidor, Dexie por conta.

## Abordagens que NÃO funcionaram
- [Reidratar o cache com o IndexedDB antes da rede](docs/learning/falha-reidratar-cache-offline-antes-da-rede.md) — o snapshot velho apagava o marcador de não lidas; use rede primeiro.

## Notas de performance

## Referências externas
