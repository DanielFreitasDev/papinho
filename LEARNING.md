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
- [Abas das configurações sumiam na Privacidade](docs/learning/bug-abas-das-configuracoes-espremidas.md) — item com overflow num flex-col é espremido; shrink-0.
- [Sync perdia mensagens com o lastSeq lido depois](docs/learning/bug-sync-lastseq-lido-depois-da-busca.md) — leia o seq antes e limite a busca; corrida provada com spy.
- [Prévia de link pedida ao mesmo tempo dava 500](docs/learning/bug-previa-de-link-simultanea.md) — save() com id atribuído faz SELECT+INSERT; trate a chave duplicada.
- [Travas do ShedLock deslocadas fora de UTC](docs/learning/bug-shedlock-com-fuso-fora-de-utc.md) — sessão JDBC sempre em UTC (connection-init-sql); options=-c TimeZone não vence o fuso da JVM.
- [Linha da conversa recorta o que passa dela](docs/learning/bug-conteudo-cortado-pela-linha-da-conversa.md) — content-visibility recorta a pintura e dá altura provisória à linha nova: reação e rabinho cabem na linha, a que entra nasce visível, o voo usa cópia fixa.
- [Banco IndexedDB "papinho-*" apagado ao abrir](docs/learning/bug-banco-indexeddb-apagado-pelo-offline.md) — o isolamento do offline apaga bancos de outra conta; dado do aparelho usa papinho.device.
- [Salto para uma das últimas mensagens deixava um vão](docs/learning/bug-vao-depois-do-salto-da-busca.md) — o MessageScroller cria um espaçador para alinhar o alvo; perto do fim, role até o fim.

## Pegadinhas de ambiente, dependências e bibliotecas
- [PKIX do JDK/BC ignora Name Constraints da âncora](docs/learning/gotcha-pkix-ignora-name-constraints-da-ancora.md) — prove a CA da LAN com openssl verify ou certutil.
- [Ambiente de nuvem: Docker, imagens e processos](docs/learning/gotcha-ambiente-nuvem-docker-e-processos.md) — iniciar dockerd, mirror.gcr.io contra 429, JDK 25, MVNW_REPOURL, cuidado com pkill -f.
- [MockMvc não serializa SameSite](docs/learning/gotcha-mockmvc-nao-serializa-samesite.md) — atributos de cookie só se provam no servidor real.
- [CSP estrita: zod JIT, <style> injetado e cache de WASM](docs/learning/gotcha-csp-estrita-zod-e-base-ui.md) — zod jitless via @/lib/zod, CSPProvider do Base UI, sem input-otp, useWasmCache=false no Whisper.
- [E2E: rate limit em memória e corrida do primeiro admin](docs/learning/gotcha-e2e-rate-limit-primeiro-admin.md) — instância nova por rodada; projeto de setup; `PAPINHO_RATE_LIMIT_MULTIPLIER` no e2e.
- [STOMP com Spring Security 7](docs/learning/gotcha-stomp-spring-security-csrf-e-heartbeat.md) — CSRF cru no CONNECT, agendador do heartbeat, WebSocket fecha com a sessão HTTP.
- [E2E do chat](docs/learning/gotcha-e2e-chat-seletores-e-presenca.md) — hasText é substring, opacidade é visível, presença antes de aceitar.
- [E2E: navegar antes de a ação terminar](docs/learning/gotcha-e2e-navegar-antes-da-acao-terminar.md) — goto cancela o POST do logout, que o servidor ainda processa; espere o /login do app.
- [Mídia: ImageIO, Uppy, MockMvc, TRUNCATE, onnxruntime](docs/learning/gotcha-midia-imagem-e-testes.md) — EXIF por segmentos, sem destroy no sucesso, URL crua no MockMvc (voltou na busca), TRUNCATE com deadlock, WebGPU só no build asyncify.
- [E2E: Chromium preinstalado, toasts e contraste](docs/learning/gotcha-e2e-chromium-preinstalado-e-a11y.md) — nome de download, headless shell sem notificações, toast é dialog, muted 0.5, axe em animação, tag por spec, Spinner/Item do shadcn, erros forçados só 4xx.
- [PWA, Web Push, busca e e2e offline](docs/learning/gotcha-pwa-push-e-busca.md) — stemmer pt (plurais -ões/-ns), ponto EC sem checagem no JDK, MIME do manifesto, tsc -b com e2e, testar a troca de service worker, produção sem assinatura de push por falta de permissão.
- [E-mail de recuperação: executor, ArchUnit e SMTP nos testes](docs/learning/gotcha-email-recuperacao-executor-e-testes.md) — sem applicationTaskExecutor com WebSocket, nada de token no outbox, GreenMail/Mailpit.
- [E2E em Firefox e WebKit](docs/learning/gotcha-e2e-firefox-e-webkit.md) — cookie Secure em localhost, colorScheme, CSP das capturas, proxy do desktop, pendências do WebKit, fetch cancelado vira pageerror, WebKit lento no runner Ubuntu 26.04.
- [IP do cliente atrás de proxy](docs/learning/gotcha-ip-do-cliente-atras-de-proxy.md) — framework confia no X-Forwarded-For do cliente; use native (RemoteIpValve).
- [Busca externa: prazo total e fora da transação](docs/learning/gotcha-busca-externa-prazo-total-e-transacao.md) — timeout é por leitura; cancel() com prazo; @Transactional esgota o Hikari; multipart em streaming.
- [Detail do PostgreSQL e erros fora do MVC](docs/learning/gotcha-detalhe-do-postgres-e-erros-fora-do-mvc.md) — logServerErrorDetail=false; /error próprio; válvula do Tomcat; socket cru no teste.
- [unaccent troca formas largas por curingas do LIKE](docs/learning/gotcha-unaccent-curingas-e-palavras.md) — escapar antes do unaccent não protege (％ vira %); prefixo com starts_with; [:punct:] inclui emoji.
- [Frontend: salto, service worker, toque e foco](docs/learning/gotcha-frontend-auditoria-2026-10-17.md) — scrollToMessage sem item, workbox.register sem catch, data-slot do Base UI, TabsContent sem foco.
- [S3 nos testes sem MinIO](docs/learning/gotcha-s3-nos-testes-sem-minio.md) — adobe/s3mock, espera pelo log, skip vira Range.
- [Máquina local: proxy no Maven, portas e /tmp](docs/learning/gotcha-ambiente-local-com-proxy.md) — proxy virava argumento do vite, e2e local em portas altas (receita), navegadores do Playwright desatualizados, gh com push só no repositório novo, proxy às vezes, ERR_NETWORK_CHANGED com Docker ao lado.
- [Correção dos Baixos de 2026-10-17](docs/learning/gotcha-correcao-baixos-2026-10-17.md) — Enter no celular, safe area pelo CDP, Mockito com varargs, mutação equivalente, 416 do Spring, shadcn add.
- [Janela de mensagens sobre o MessageScroller](docs/learning/gotcha-janela-de-mensagens-e-message-scroller.md) — âncora que volta ao DOM salta, root e observador recriado após o commit, lado do corte pela borda, compensação pela linha visível.
- [Spring Modulith 2.1.2: proxy nas implementações internas da API](docs/learning/gotcha-modulith-proxy-de-observabilidade.md) — getClass() vira CGLIB; use AopUtils.getTargetClass e nada de método final.
- [Duração de áudio: WebM do MediaRecorder, pre-skip e Tika](docs/learning/gotcha-duracao-de-audio-mediarecorder.md) — fim do último bloco − início − CodecDelay; Firefox grava Duration 0,0; .aac e .flac viravam documento.
- [JSON dos idiomas e CSS fora do format:check](docs/learning/gotcha-json-dos-idiomas-reordena-chaves.md) — reescrever o JSON reordena chaves numéricas; prettier no CSS reformata regras alheias.
- [Animações nos testes e no roteador](docs/learning/gotcha-animacoes-e-testes.md) — CDP desacelera o rAF e não os timers, resolvedLocation na view transition, toque longo sem clique, caixa durante a escala, ready da transição interrompida vira erro, toque durante a view transition.
- [Produção na VPS atrás do Cloudflare Tunnel](docs/learning/gotcha-producao-vps-cloudflare-tunnel.md) — admin pelo loopback antes do hostname, cookie Secure no curl, beacon barrado pela CSP, e2e lento e cota de artifacts no repositório privado.

## Decisões de arquitetura
- As decisões vivem em `docs/adr/` (0001–0015).
- [Fase 4: grupos, transmissão, temporárias, visualização única, figurinhas](docs/learning/decisao-fase4-mensagens-avancadas.md) — limites e escolhas que o código não diz.
- [Fase 5: privacidade](docs/learning/decisao-fase5-privacidade.md) — reciprocidade por par, leitura no grupo, token das trancadas, cache offline.
- [Fase 6: lista, busca, notificações e offline](docs/learning/decisao-fase6-organizacao-notificacoes.md) — arquivo calculado, apagar com removed_seq, push decidido no servidor, Dexie por conta.
- [UI-05: aparência própria](docs/learning/decisao-ui05-aparencia-propria.md) — data/hora, senha, busca, mídia, noValidate, regra de lint e a prova no e2e.
- [Destaque da busca pela mesma regra do servidor](docs/learning/decisao-destaque-da-busca.md) — Snowball português igual ao portuguese_stem, fixture conferida nos dois lados, termo pelo estado do histórico.
- [Escala de espaçamento e auditoria das bordas](docs/learning/decisao-escala-de-espacamento.md) — tokens na forma de variável (cn), menu com largura própria, fieldset min-w-0, addons presos embaixo, data-scroll-x.
- [Animações sem biblioteca, com tokens e só ao vivo](docs/learning/decisao-animacoes.md) — nada de Motion nem ViewTransition do React na lista, entrada decidida antes de pintar, chave remetente+clientMessageId, bola, onda e voo.
- [Busca de pessoas por nome no "Adicionar contato"](docs/learning/decisao-busca-de-pessoas-por-nome.md) — todos aparecem e cada um pode sair; @usuario exato e contatos sempre acham; bloqueio nunca.
- [Formatação no campo com um espelho](docs/learning/decisao-formatacao-no-campo.md) — marcadores do WhatsApp, espelho que não muda a largura das letras, insertText para o desfazer, prova letra por letra.
- [Paletas de cor e papéis de parede](docs/learning/decisao-paletas-e-papel-de-parede.md) — OKLCH por fórmula com contraste conferido, hover por color-mix, doodles do Lucide como máscara, imagem só no aparelho.
- [Licença AGPL-3.0 e imagem Docker no GHCR](docs/learning/decisao-licenca-e-imagem-docker.md) — permissão da seção 7 para o AspectJ, Alpine com o JAR testado via build-context, release idempotente.
- [Repositório público e o runner self-hosted](docs/learning/decisao-repositorio-publico.md) — cota de minutos da conta esgotada no privado, gitleaks antes de abrir, aprovação de todo PR de fora (o runner tem root na VPS).
- [Limpeza dos arquivos nunca usados](docs/learning/decisao-limpeza-de-arquivos-nao-usados.md) — porta MediaReferences, in_use preguiçoso, trava contra o anexo simultâneo.
- [Correção da auditoria de 2026-10-17](docs/learning/decisao-auditoria-2026-10-17.md) — convite do admin no fechado, foto view-once, HEIC recusado, exportação sem trancadas, presença.
- [Correção dos Baixos de 2026-10-17](docs/learning/decisao-auditoria-baixos-2026-10-17.md) — login por IP e conta, grant dos anexos trancados, reservas da conta apagada, antivírus fora.

## Abordagens que NÃO funcionaram
- [Reidratar o cache com o IndexedDB antes da rede](docs/learning/falha-reidratar-cache-offline-antes-da-rede.md) — o snapshot velho apagava o marcador de não lidas; use rede primeiro.

## Notas de performance

## Referências externas
