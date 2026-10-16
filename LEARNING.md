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
- [Recargas canceladas em rajada derrubavam o HTTP/2](docs/learning/bug-recarga-cancelada-derruba-http2.md) — invalidateQueries vira RST_STREAM; use refreshQueries.
- [Cadastros em fila pelo advisory lock do primeiro usuário](docs/learning/bug-cadastro-serializado-pela-trava.md) — trava global presa durante 11 hashes Argon2; só com a instância vazia.
- [Invalidar lista ainda carregando não traz a mudança](docs/learning/bug-invalidar-lista-ainda-carregando.md) — invalidate sem dados reaproveita a 1ª busca; use refreshQueries.
- [Hover grudado no toque revelava o modo privado](docs/learning/bug-hover-grudado-no-toque.md) — revelar com :hover só em @media (hover: hover); toque longo de verdade pelo CDP.

## Pegadinhas de ambiente, dependências e bibliotecas
- [PKIX do JDK/BC ignora Name Constraints da âncora](docs/learning/gotcha-pkix-ignora-name-constraints-da-ancora.md) — prove a CA da LAN com openssl verify ou certutil.
- [Ambiente de nuvem: Docker, imagens e processos](docs/learning/gotcha-ambiente-nuvem-docker-e-processos.md) — iniciar dockerd, mirror.gcr.io contra 429, JDK 25, MVNW_REPOURL, cuidado com pkill -f.
- [MockMvc não serializa SameSite](docs/learning/gotcha-mockmvc-nao-serializa-samesite.md) — atributos de cookie só se provam no servidor real.
- [CSP estrita: zod JIT, <style> injetado e cache de WASM](docs/learning/gotcha-csp-estrita-zod-e-base-ui.md) — zod jitless via @/lib/zod, CSPProvider do Base UI, sem input-otp, useWasmCache=false no Whisper.
- [E2E: rate limit em memória e corrida do primeiro admin](docs/learning/gotcha-e2e-rate-limit-primeiro-admin.md) — instância nova por rodada; projeto de setup; `PAPINHO_RATE_LIMIT_MULTIPLIER` no e2e.
- [STOMP com Spring Security 7](docs/learning/gotcha-stomp-spring-security-csrf-e-heartbeat.md) — CSRF cru no CONNECT, agendador do heartbeat, WebSocket fecha com a sessão HTTP.
- [E2E do chat](docs/learning/gotcha-e2e-chat-seletores-e-presenca.md) — hasText é substring, opacidade é visível, presença antes de aceitar.
- [Mídia: ImageIO, Uppy, MockMvc, TRUNCATE, onnxruntime](docs/learning/gotcha-midia-imagem-e-testes.md) — EXIF por segmentos, sem destroy no sucesso, URL crua no MockMvc, TRUNCATE com deadlock, WebGPU só no build asyncify.
- [E2E: Chromium preinstalado, toasts e contraste](docs/learning/gotcha-e2e-chromium-preinstalado-e-a11y.md) — nome de download, headless shell sem notificações, toast é dialog, muted 0.5, axe em animação, tag por spec, Spinner/Item do shadcn, erros forçados só 4xx.
- [PWA, Web Push, busca e e2e offline](docs/learning/gotcha-pwa-push-e-busca.md) — stemmer pt, ponto EC sem checagem no JDK, MIME do manifesto, tsc -b com e2e, testar a troca de service worker.
- [E-mail de recuperação: executor, ArchUnit e SMTP nos testes](docs/learning/gotcha-email-recuperacao-executor-e-testes.md) — sem applicationTaskExecutor com WebSocket, nada de token no outbox, GreenMail/Mailpit.
- [E2E em Firefox e WebKit](docs/learning/gotcha-e2e-firefox-e-webkit.md) — cookie Secure em localhost, colorScheme, CSP das capturas, proxy do desktop, pendências do WebKit.
- [IP do cliente atrás de proxy](docs/learning/gotcha-ip-do-cliente-atras-de-proxy.md) — framework confia no X-Forwarded-For do cliente; use native (RemoteIpValve).
- [Máquina local: proxy no Maven, portas e /tmp](docs/learning/gotcha-ambiente-local-com-proxy.md) — proxy virava argumento do vite, e2e em portas altas, gh sem push, jcmd, internet só pelo proxy, 12 workers.

## Decisões de arquitetura
- As decisões vivem em `docs/adr/` (0001–0015).
- [Fase 4: grupos, transmissão, temporárias, visualização única, figurinhas](docs/learning/decisao-fase4-mensagens-avancadas.md) — limites e escolhas que o código não diz.
- [Fase 5: privacidade](docs/learning/decisao-fase5-privacidade.md) — reciprocidade por par, leitura no grupo, token das trancadas, cache offline.
- [Fase 6: lista, busca, notificações e offline](docs/learning/decisao-fase6-organizacao-notificacoes.md) — arquivo calculado, apagar com removed_seq, push decidido no servidor, Dexie por conta.
- [UI-05: aparência própria](docs/learning/decisao-ui05-aparencia-propria.md) — data/hora, senha, busca, mídia, noValidate, regra de lint e a prova no e2e.
- [Limpeza dos arquivos nunca usados](docs/learning/decisao-limpeza-de-arquivos-nao-usados.md) — porta MediaReferences, in_use preguiçoso, trava contra o anexo simultâneo.

## Abordagens que NÃO funcionaram
- [Reidratar o cache com o IndexedDB antes da rede](docs/learning/falha-reidratar-cache-offline-antes-da-rede.md) — o snapshot velho apagava o marcador de não lidas; use rede primeiro.

## Notas de performance

## Referências externas
